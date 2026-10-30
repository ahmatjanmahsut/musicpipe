import fs from "node:fs";
import path from "node:path";
import { ENV, cfg } from "../config.js";
import { getDb } from "../db/index.js";
import { newId, sanitizeName, RateLimiter, sleep, fetchWithTimeout } from "../util.js";
import { getBox, boxForPlatform, boxesForPlatform } from "../sources/registry.js";
import { getPlayUrlWithFallback } from "../sources/adapter.js";
import { getLyric } from "../sources/lyric.js";
import { fetchCoverBuf } from "./cover.js";
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
  if (!magic.ok) { try { fs.unlinkSync(localPath); } catch (e) {} throw new Error("校验失败: " + magic.reason); }
  var actualExt = magic.magic === "unknown" ? ext : magic.magic;
  if (actualExt !== ext) { var np = localPath.replace(/\.[^.]+$/, "." + actualExt); try { fs.renameSync(localPath, np); localPath = np; setLocal(localPath); } catch (e) {} ext = actualExt; }
  var size0 = fs.statSync(localPath).size;
  if (size0 < 1000) { try { fs.unlinkSync(localPath); } catch (e) {} throw new Error("文件过小，疑似试听片段"); }

  // 4. 标签
  onStatus("tagging", { message: "写入标签" });
  var meta = { title: song.name || "Unknown", artist: song.singer || "未知歌手", album: song.album || "单曲", year: song.year || "", genre: "" };
  var share = payload.share || null;
  if (share && share.album) {
    meta.album = share.album;
    meta.albumArtist = share.folder || share.album;
    meta.trackNumber = String(share.no || "");
  }
  try { meta.lyrics = await getLyric(song.source, song); } catch (e) {}
  meta.coverUrl = song.img || "";
  // v6: 封面只下载一次 —— 既用于内嵌标签，也作为 .jpg sidecar 上传（与歌词一致）
  var coverBuf = null;
  try { coverBuf = await fetchCoverBuf(song.img || ""); } catch (e) {}
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
  // 封面 sidecar 同步上传（同名 .jpg，存储方式与歌词 .lrc 一致）
  try {
    if (coverBuf) {
      var imgRel = "_incoming/" + incomingName.replace(/\.[a-z0-9]+$/i, "") + ".jpg";
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
