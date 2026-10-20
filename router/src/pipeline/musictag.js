import { ENV, cfg } from "../config.js";
import { fetchWithTimeout } from "../util.js";
import { logEvent } from "../db/index.js";
import { cfg as _cfg } from "../config.js";

function base() { return String(cfg("musictag_url", ENV.musictagUrl)).replace(/\/+$/, ""); }
function mediaRoot() { return String(cfg("mt_media_root", "/app/media")); }

export async function mtProbe() {
  try {
    var r = await fetchWithTimeout(base() + "/", {}, 8000);
    return { ok: r.status < 500, status: r.status };
  } catch (e) { return { ok: false, error: String(e.message || e) }; }
}

// 凭证优先级：settings.mt_token（长期JWT，形如 "JWT eyJ..."）> mt_user/mt_pass 登录
function authHeader() {
  var t = String(cfg("mt_token", "") || "").trim();
  if (t) return t.startsWith("JWT ") || t.startsWith("Bearer ") ? t : "JWT " + t;
  return null;
}

async function mtPost(path, body, ms) {
  var auth = authHeader();
  if (!auth) throw new Error("未配置 Music Tag Web 凭证（设置页「MTW Token」）");
  var r = await fetchWithTimeout(base() + path, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: auth },
    body: JSON.stringify(body)
  }, ms || 60000);
  if (r.status === 401 || r.status === 403) throw new Error("MTW 凭证无效或过期（HTTP " + r.status + "），请更新设置页 mt_token");
  var j = await r.json().catch(function () { return { rawStatus: r.status }; });
  if (j && j.result === false) throw new Error(path + " 失败: " + String(j.message || JSON.stringify(j)).slice(0, 150));
  return j && j.data !== undefined ? j.data : j;
}

function sim(a, b) {
  a = String(a || "").toLowerCase(); b = String(b || "").toLowerCase();
  if (!a || !b) return 0;
  if (a === b) return 1;
  if (a.includes(b) || b.includes(a)) return 0.8;
  var n = Math.min(a.length, b.length), c = 0;
  for (var i = 0; i < n; i++) { if (a[i] === b[i]) c++; }
  return c / Math.max(a.length, b.length);
}

// 完整免费刮削链：读现标签 -> 搜候选 -> 选最优 -> 取歌词 -> 合并写回
export async function mtScrapeTrack(track) {
  if (!track || !track.remote_path) return { ok: false, reason: "无文件" };
  try {
    var rel = String(track.remote_path);
    var davRoot = String(cfg("webdav_root", "/music")).replace(/^\/+|\/+$/g, "");
    var fullRel = (davRoot ? davRoot + "/" : "") + rel;
    var dirRel = fullRel.split("/").slice(0, -1).join("/");
    var fname = rel.split("/").pop();
    var dirAbs = mediaRoot().replace(/\/+$/, "") + "/" + dirRel;
    var resource = String(cfg("mt_sources", "netease")).split(",")[0].trim() || "netease";
    var id3 = await mtPost("/apimt/music_id3/", { file_path: dirAbs, file_name: fname }, 30000);
    if (!id3 || !id3.filename) throw new Error("music_id3 无数据");
    var cands = await mtPost("/apimt/fetch_id3_by_title/", { title: id3.title || fname, resource: resource }, 40000);
    var arr = Array.isArray(cands) ? cands : (cands && cands.data) || [];
    if (!arr.length) throw new Error("无匹配候选");
    var wantArtist = (track.artist || id3.artist || "");
    var best = arr[0], bs = -1;
    for (var i = 0; i < Math.min(arr.length, 10); i++) {
      var s = sim(arr[i].singer, wantArtist) + sim(arr[i].title, id3.title) * 0.5;
      if (s > bs) { bs = s; best = arr[i]; }
    }
    var lyric = id3.lyrics || "";
    try {
      var lid = best.musicid || best.id || best.songmid;
      if (lid) {
        var ly = await mtPost("/apimt/fetch_lyric/", { song_id: String(lid), resource: resource }, 30000);
        var txt = typeof ly === "string" ? ly : (ly && (ly.lyric || ly.text || (ly.lyrics && (ly.lyrics.lyric || ly.lyrics.text)))) || "";
        if (txt && /\[\d\d:/.test(txt)) lyric = txt;
      }
    } catch (e) { /* 歌词失败不阻塞 */ }
    var merged = Object.assign({}, id3, {
      title: best.title || id3.title,
      artist: best.singer || best.artist || id3.artist,
      artist_list: String(best.singer || id3.artist || "").split(/[,，、]/).map(function (x) { return x.trim(); }).filter(Boolean),
      album: best.album || id3.album,
      albumartist: best.albumartist || best.author_simple || id3.albumartist || "",
      year: String(best.year || id3.year || ""),
      tracknumber: best.tracknumber != null ? String(best.tracknumber) : (id3.tracknumber || ""),
      discnumber: best.discnumber != null ? String(best.discnumber) : (id3.discnumber || ""),
      totaltracks: best.totaltracks != null ? String(best.totaltracks) : (id3.totaltracks || ""),
      totaldiscs: best.totaldiscs != null ? String(best.totaldiscs) : (id3.totaldiscs || ""),
      genre: best.genre || id3.genre || "",
      language: best.language || id3.language || "",
      composer: best.composer || id3.composer || "",
      lyricist: best.lyricist || id3.lyricist || "",
      label: best.label || id3.label || "",
      date: best.time_publish ? new Date(Number(best.time_publish)).toISOString().slice(0, 10) : (id3.date || ""),
      artwork: best.album_img || id3.artwork || "",
      lyrics: lyric,
      file_full_path: id3.path || (dirAbs + "/" + fname),
    });
    var res = await mtPost("/apimt/update_id3/", { music_id3_info: [merged] }, 120000);
    // 有歌词时同步写 .lrc sidecar（播放器/Navidrome 高兼容）
    if (lyric) {
      try {
        var lrcRel = rel.replace(/\.[a-z0-9]+$/i, "") + ".lrc";
        var { putBuffer } = await import("../webdav/client.js");
        await putBuffer(lrcRel, lyric);
      } catch (e) { console.warn("[mtw] write lrc failed:", e.message); }
    }
    // 有封面时同步写 .jpg sidecar（与歌词 .lrc 相同的存储方式）
    try {
      var art = String(merged.artwork || "");
      if (/^https?:/i.test(art)) {
        var { fetchCoverBuf } = await import("../downloader/cover.js");
        var ab = await fetchCoverBuf(art);
        if (ab) {
          var { putBuffer } = await import("../webdav/client.js");
          await putBuffer(rel.replace(/\.[a-z0-9]+$/i, "") + ".jpg", ab);
        }
      }
    } catch (e) { console.warn("[mtw] write cover failed:", e.message); }
    logEvent("mt_scrape", track.title + " -> " + merged.album + " 命中:" + (best.singer || "") + " 歌词:" + (lyric ? "有" : "无"));
    return { ok: true, matched: { title: best.title, singer: best.singer, album: best.album }, lyric: !!lyric };
  } catch (e) {
    return { ok: false, reason: String(e.message || e) };
  }
}

// 兼容旧引用
export async function mtLogin() { var a = authHeader(); if (!a) throw new Error("未配置 mt_token"); return a; }