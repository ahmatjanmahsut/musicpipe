import fs from "node:fs";
import path from "node:path";
import { ENV, cfg } from "../config.js";
import { getDb } from "../db/index.js";
import { newId, sanitizeName, RateLimiter, sleep, fetchWithTimeout } from "../util.js";
import { getBox, boxForPlatform, boxesForPlatform } from "../sources/registry.js";
import { getPlayUrlWithFallback, sniffAudioType } from "../sources/adapter.js";
import { getLyric } from "../sources/lyric.js";
import { fetchCoverBuf } from "./cover.js";
import { lookupCoverUrl } from "./coverlookup.js";
import { writeTags, checkMagic } from "./tagger.js";
import { updateTask, notify } from "./queue.js";
import { putStream, statRemote, davPath, ensureDirsFor, listRemote, moveRemote, webdavReady, putBuffer } from "../webdav/client.js";
import { refreshDir } from "../webdav/rclone.js";
import { organizeTrack } from "../pipeline/organizer.js";
import { logEvent } from "../db/index.js";

var globalLimiter = new RateLimiter(0);
var limiterKbps = -1;
function getLimiter() {
  var k = Number(cfg("dl_speed_limit_kbps", 0));
  if (k !== limiterKbps) { globalLimiter = new RateLimiter(k); limiterKbps = k; }
  return globalLimiter;
}

function q2ext(q) { return q === "flac" || q === "flac24bit" ? "flac" : (q === "m4a" ? "m4a" : "mp3"); }

/**
 * v1.9.0：把「分享来源」的元数据合并进曲目标签。
 *
 * 旧代码在 if (share && share.album) 里无条件用 share.album 覆盖真实专辑，而服务端当时把
 * share.album 设成 folder（分享者昵称），于是整个歌单 101 首的 album 全变成 "demo"。
 * 现在默认 share.album 为 null，这里保留曲目自己的专辑；只有显式配置 share_album_by 才覆盖。
 * folder / no 仍照常写 albumArtist / trackNumber（"来自哪张歌单、第几首"与专辑无关）。
 *
 * 提取成纯函数是为了能被探针/单测直接调用，不必真的下载一首歌来验证。
 */
export function applyShareMeta(meta, share) {
  if (!share) return meta;
  if (share.album) meta.album = share.album;
  if (share.folder) meta.albumArtist = share.folder;
  if (share.no) meta.trackNumber = String(share.no);
  return meta;
}

function checkAbort(ctl) {
  if (ctl.aborted) throw new Error("cancelled-" + ctl.aborted);
}

// 主流程。onStatus(status, extra) 由 queue 提供。
export async function runTask(taskId, payload, ctl, onStatus) {
  var localPath = null;
  try {
    return await runTaskInner(taskId, payload, ctl, onStatus, function (lp) { localPath = lp; });
  } finally {
    if (localPath) { try { fs.unlinkSync(localPath); } catch (e) {} }
    // 分享歌单任务：执行后强制间隔，避免音源查询过载
    if (payload && payload.share) {
      var gap = Math.max(2, Number(cfg("share_gap_sec", 6)) || 6);
      await sleep(gap * 1000);
    }
  }
}

async function runTaskInner(taskId, payload, ctl, onStatus, setLocal) {
  checkAbort(ctl);
  var song = payload.song;
  var want = payload.quality || "320k";
  var boxesPool = boxesForPlatform(song.source, payload.source_id);
  if (!boxesPool.length) throw new Error("没有支持平台 " + song.source + " 的可用音源");
  var box = null;
  var boxId = payload.source_id || "auto";

  // 1. 获取播放地址（平台降级）
  onStatus("downloading", { message: "获取播放地址...", progress: 0 });
  console.log("[task]", taskId, "dispatch musicUrl", song.source, want, "via", boxesPool.length, "sources");
  var got = null, lastUrlErr = null;
  for (var bi = 0; bi < boxesPool.length; bi++) {
    box = boxesPool[bi].box; boxId = boxesPool[bi].id;
    try { got = await getPlayUrlWithFallback(box, song.source, song, want); break; }
    catch (e) { lastUrlErr = e; }
  }
  if (!got) throw new Error("全部 " + boxesPool.length + " 个音源取链失败: " + String((lastUrlErr && lastUrlErr.message) || "").slice(0, 120));
  console.log("[task]", taskId, "got url q=" + got.quality, String(got.url).slice(0, 80));
  var url = got.url, quality = got.quality;
  var ext = q2ext(quality);
  checkAbort(ctl);

  // v1.6.0：音源声称支持的音质并不可信 —— want=flac 时它可能返回 128k 的 mp3，
  // 或者干脆返回一段非音频内容（例如 "refuse request!" 之后兜底到的加密/压缩流）。
  // 若照旧按"想要的音质"推导扩展名，落盘就成了 mp3 内容 + .flac 扩展名，
  // 于是 checkMagic 必然报「文件头(mp3)与扩展名(flac)不符」或「无法识别的音频文件头」。
  // 这里先嗅探真实容器：能识别就按真实格式修正扩展名与音质；识别不出（null）
  // 说明音源给的压根不是音频，直接判失败并给出可操作的提示，而不是把垃圾写进曲库。
  var sniffed = null;
  try { sniffed = await sniffAudioType(url); } catch (e) { sniffed = null; }
  if (sniffed && sniffed !== ext) {
    console.log("[task]", taskId, "container override", ext, "->", sniffed, "(declared q=" + quality + ")");
    logEvent("quality_override", (song.name || "") + " 音源声明 " + quality + " 实际 " + sniffed);
    ext = sniffed;
    if (sniffed === "mp3") quality = quality === "flac" || quality === "flac24bit" ? "320k" : quality;
    else if (sniffed === "flac") quality = quality === "flac24bit" ? quality : "flac";
  } else if (!sniffed) {
    // 取不到文件头（网络/防盗链）：保持原有推导，让后面的 checkMagic 去做最终判定
    console.log("[task]", taskId, "sniff unavailable, keep ext=" + ext);
  }

  // 2. 下载
  var localName = taskId + "." + ext;
  var localPath = path.join(ENV.stagingDir, localName);
  setLocal(localPath);
  fs.mkdirSync(ENV.stagingDir, { recursive: true });
  await streamDownload(url, localPath, ctl, function (loaded, total, bytesPerSec) {
    var pct = total ? Math.floor(loaded / total * 90) : Math.min(90, Math.floor(loaded / 500000));
    updateTask(taskId, { progress: pct, speed: bytesPerSec, message: "下载中 " + Math.floor(loaded / 1048576 * 10) / 10 + "MB" });
  });
  checkAbort(ctl);

  // 3. 校验
  onStatus("verifying", { message: "校验文件" });
  console.log("[task]", taskId, "downloaded, verify");
  var magic = checkMagic(localPath, ext);
  if (!magic.ok) {
    try { fs.unlinkSync(localPath); } catch (e) {}
    // v1.6.0：报错要给用户可操作的出路，而不是"校验失败"四个字。
    // 嗅探和 checkMagic 都说"不是音频" = 音源在 want 这个音质上根本没给音频
    // （常见于曲目只有 128k、却请求了 flac）。此时唯一有效的动作是降低音质重试。
    var reason = magic.reason;
    if (sniffed === null && (reason === "无法识别的音频文件头" || String(reason).indexOf("不符") >= 0)) {
      logEvent("quality_unavailable", (song.name || "") + " want=" + quality + " 音源返回非音频内容");
      throw new Error("该曲目没有 " + quality + " 音质的资源，请把音质改成「自动」或 128kbps 后重试");
    }
    throw new Error("校验失败: " + reason + "（请把音质改成「自动」或更低音质后重试）");
  }
  var actualExt = magic.magic === "unknown" ? ext : magic.magic;
  if (actualExt !== ext) { var np = localPath.replace(/\.[^.]+$/, "." + actualExt); try { fs.renameSync(localPath, np); localPath = np; setLocal(localPath); } catch (e) {} ext = actualExt; }
  var size0 = fs.statSync(localPath).size;
  if (size0 < 1000) { try { fs.unlinkSync(localPath); } catch (e) {} throw new Error("文件过小，疑似试听片段"); }

  // 4. 标签
  onStatus("tagging", { message: "写入标签" });
  var meta = { title: song.name || "Unknown", artist: song.singer || "未知歌手", album: song.album || "单曲", year: song.year || "", genre: "" };
  var share = payload.share || null;
  applyShareMeta(meta, share);
  try { meta.lyrics = await getLyric(song.source, song); } catch (e) {}
  // v1.9.0 回归修复：v1.6.0 部署时用旧副本覆盖了 runner.js，把 v1.4.0 的封面抓取整段丢了
  // —— 之后下载的歌既无内嵌封面、也无 .jpg sidecar，App 里封面全空白。
  // 封面只抓一次：既用于内嵌标签，也作为 sidecar 上传（与歌词一致）。
  //
  // v1.9.1：kw/kg/tx 的搜索结果 img 恒为 null，这类歌原本永远没有封面。
  // 这里在 img 为空时按"歌名+歌手"反查一次（见 coverlookup.js），查不准就放弃。
  var coverSrc = song.img || "";
  if (!coverSrc) { try { coverSrc = (await lookupCoverUrl(song.name, song.artist || song.singer)) || ""; } catch (e) { coverSrc = ""; } }
  meta.coverUrl = coverSrc;
  var coverBuf = null;
  try { coverBuf = await fetchCoverBuf(coverSrc); } catch (e) {}
  var tagMeta = coverBuf ? Object.assign({}, meta, { coverBuf: coverBuf }) : meta;
  var tagRes = await writeTags(localPath, ext, tagMeta);
  if (!tagRes.ok) logEvent("tag_warn", song.name + ": " + tagRes.error);
  checkAbort(ctl);
  // 标签写入会改变文件大小，上传与校验一律以最终大小为准
  var size = fs.statSync(localPath).size;

  // 5. 上传 WebDAV
  if (!webdavReady()) throw new Error("WebDAV 网盘未配置（设置页）");
  var incomingName = sanitizeName(meta.artist) + " - " + sanitizeName(meta.title) + " [" + taskId.slice(-6) + "]." + ext;
  var relIncoming = "_incoming/" + incomingName;
  var trackId = newId();
  var uploaded = false;
  onStatus("uploading", { message: "上传网盘", progress: 91 });
  console.log("[task]", taskId, "uploading", relIncoming);
  await putStream(relIncoming, localPath, function (loaded) {
    var pct = Math.min(99, 91 + Math.floor(loaded / size * 8));
    updateTask(taskId, { progress: pct, speed: 0, message: "上传中 " + Math.floor(loaded / 1048576 * 10) / 10 + "MB" });
  });
  // 远端校验（最多重试 5 次等待服务端落盘）
  var rs = null, rtry = 0;
  while (rtry < 5) {
    rs = await statRemote(relIncoming);
    if (rs && Math.abs((rs.size || 0) - size) <= Math.max(2, size * 0.001)) break;
    await sleep(1000); rtry++;
  }
  if (!rs || Math.abs((rs.size || 0) - size) > Math.max(2, size * 0.001)) {
    throw new Error("远端校验失败 expected=" + size + " got=" + (rs ? rs.size : "null") + " after " + rtry + " retries");
  }
  // 歌词 sidecar 同步上传（.lrc 兼容性最好）
  try {
    if (meta.lyrics && /\[\d\d:/.test(meta.lyrics)) {
      var lrcRel = "_incoming/" + incomingName.replace(/\.[a-z0-9]+$/i, "") + ".lrc";
      await putBuffer(lrcRel, meta.lyrics);
    }
  } catch (e) { logEvent("lrc_warn", String(e.message || e)); }
  // 封面 sidecar 同步上传（同名 .jpg，方式与歌词 .lrc 一致）
  try {
    if (coverBuf) {
      var imgRel = "_incoming/" + incomingName.replace(/.[a-z0-9]+$/i, "") + ".jpg";
      await putBuffer(imgRel, coverBuf);
    }
  } catch (e2) { logEvent("cover_warn", String(e2.message || e2)); }
  uploaded = true;
  try { fs.unlinkSync(localPath); } catch (e) {}
  try { await refreshDir("music", true); } catch (e) {}

  // 记录 tracks
  var dbMod = await import("../db/index.js");
  var now = Date.now();
  dbMod.getDb().prepare("INSERT INTO tracks(id,title,artist,album,platform,song_id,quality,file_path,remote_path,file_size,status,source_id,task_id,duration,extra,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
    .run(trackId, meta.title, meta.artist, meta.album, song.source, String(song.id), quality, "", relIncoming, size, "uploaded", boxId, taskId, song.duration || 0, JSON.stringify({ meta: meta, types: song.types || [], share: share || null }), now, now);

  // 6. 自动整理入库
  if (cfg("auto_organize", true) === true || cfg("auto_organize", "true") === "true") {
    if (share && share.link_id && taskId) {
      try { getDb().prepare("UPDATE sharelink_items SET track_id=?, state='done' WHERE link_id=? AND task_id=?").run(trackId, share.link_id, taskId); } catch (e) {}
    }
    try {
      onStatus("organizing", { message: "整理入库" });
      await organizeTrack(trackId);
      // 整理完成后延迟触发 Navidrome 增量扫描
      setTimeout(function () { import("../pipeline/navidrome.js").then(function (m) { m.startScan().catch(function () {}); }); }, 4000);
    } catch (e) {
      logEvent("organize_warn", "track " + trackId + ": " + String(e.message || e));
    }
  }
  updateTask(taskId, { track_id: trackId });
  return { trackId: trackId, remotePath: relIncoming, quality: quality };
}

function boxesList2() { try { return boxesForPlatform("__count__"); } catch (e) { return []; } }
async function streamDownload(url, dest, ctl, onProgress, retries) {
  retries = retries || 0;
  var limiter = getLimiter();
  for (var attempt = 0; attempt <= 2; attempt++) {
    try {
      var ctl = new AbortController();
      var ft = setTimeout(function () { ctl.abort(new Error("下载超时90s")); }, 90000);
      var r = await fetch(url, { headers: { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MusicPipe/1.0" }, redirect: "follow", signal: ctl.signal });
      clearTimeout(ft);
      if (!r.ok || !r.body) throw new Error("HTTP " + r.status);
      var total = Number(r.headers.get("content-length") || 0);
      var out = fs.createWriteStream(dest);
      var loaded = 0, lastT = Date.now(), lastLoaded = 0, speed = 0;
      void speed;
      try {
        for await (var chunk of r.body) {
          checkAbort(ctl);
          loaded += chunk.length;
          out.write(chunk);
          await limiter.wait(chunk.length);
          var now = Date.now();
          if (now - lastT >= 800) {
            speed = Math.floor((loaded - lastLoaded) / ((now - lastT) / 1000));
            lastT = now; lastLoaded = loaded;
            onProgress(loaded, total, speed);
          }
        }
      } catch (e) {
        if (String(e.message || e).indexOf("cancelled") >= 0) throw e;
        out.end();
        if (attempt === 2) throw e;
        await sleep(1500 * (attempt + 1));
        continue;
      }
      await new Promise(function (res, rej) { out.end(function () { res(); }); out.on("error", rej); });
      return;
    } catch (e) {
      if (String(e.message || e).indexOf("cancelled") >= 0) throw e;
      if (attempt === 2) throw e;
      await sleep(1500 * (attempt + 1));
    }
  }
}
