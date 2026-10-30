import { getDb, logEvent } from "./db/index.js";
import { cfg } from "./config.js";
import { fetchWithTimeout } from "./util.js";
import { search as platSearch, PLATFORM_NAMES } from "./sources/search.js";
import { enqueueDownload } from "./downloader/queue.js";
import { navRequest, startScan, scanStatus, getJwt } from "./pipeline/navidrome.js";
import { prepareShareLink, getSharePreview, commitShareLink, syncShareLink } from "./pipeline/sharelink.js";
import { getClient, davPath, deleteRemote as delRemote } from "./webdav/client.js";
import { refreshDir as refreshShare } from "./webdav/rclone.js";
import { cancelTask as cancelShareTask } from "./downloader/queue.js";
import { sniffImageType } from "./downloader/cover.js";
import { cachedAudio, prefetchAudio, audioCacheStats } from "./services/audioCache.js";

const PLATS = ["kw", "kg", "tx", "wy"];
var ndMap = new Map();      // trackId -> {ndSongId, coverArt, ts}
var coverCache = new Map(); // key -> {buf, type, ts}

function err(reply, code, msg) { reply.code(code); return { error: msg }; }

function trackBrief(r) {
  return {
    id: r.id, title: r.title, artist: r.artist, album: r.album,
    quality: r.quality, duration: r.duration, size: r.file_size,
    path: r.remote_path, status: r.status, platform: r.platform,
    importedAt: r.updated_at, created_at: r.created_at,
    streamUrl: "/api/v1/stream/" + r.id,
    coverUrl: "/api/v1/cover/" + r.id,
    lyricsUrl: "/api/v1/lyrics/" + r.id,
  };
}

// 拿 ND 曲库映射（songId/coverArt）：先 search3 精确匹配，10 分钟缓存
async function ndOf(track) {
  var c = ndMap.get(track.id);
  if (c && Date.now() - c.ts < 600000) return c;
  var out = { ndSongId: "", coverArt: "", ts: Date.now() };
  try {
    var resp = await navRequest("search3", "q=" + encodeURIComponent(track.title) + "&songCount=30");
    var songs = (resp.searchResult3 || {}).song || [];
    var hit = songs.find(function (x) { return x.title === track.title && (!track.artist || (x.artist || "").includes(track.artist.slice(0, 3))); }) || songs[0];
    if (hit) { out.ndSongId = hit.id; out.coverArt = hit.coverArt || ""; }
  } catch (e) {}
  ndMap.set(track.id, out);
  return out;
}

function parseLrc(text) {
  var out = [];
  String(text).split("\n").forEach(function (line) {
    var m = line.match(/^\[(\d\d):(\d\d)(?:[.:](\d{1,3}))?\](.*)$/);
    if (m) {
      var ms = Number(m[1]) * 60000 + Number(m[2]) * 1000 + (m[3] ? Number((m[3] + "00").slice(0, 3)) : 0);
      out.push({ start: ms, line: m[4].trim() });
    }
  });
  return out;
}

async function lrcText(track) {
  // 1) 网盘同名 .lrc  sidecar
  try {
    var lrcPath = davPath(String(track.remote_path).replace(/\.[a-z0-9]+$/i, "") + ".lrc");
    var buf = await getClient().getFileContents(lrcPath);
    var s = typeof buf === "string" ? buf : String(buf);
    if (s && /\[\d\d:/.test(s)) return s;
  } catch (e) {}
  // 2) Navidrome 结构化歌词
  try {
    var nd = await ndOf(track);
    if (nd.ndSongId) {
      var resp = await navRequest("getLyricsBySongId", "id=" + nd.ndSongId);
      var list = (((resp.lyricsBySongId || {}).lyricsList || {}).structuredLyrics || {}).entry || [];
      if (list.length) {
        return list.map(function (e2) { return (e2.line || []).join("\n"); }).join("\n");
      }
    }
  } catch (e) {}
  // 3) 内嵌歌词（MTW读取口）
  try {
    var { mtScrapeRead } = await import("./pipeline/musictag.js");
    var cur = await mtScrapeRead(track);
    if (cur && cur.lyrics && cur.lyrics.length > 20) return cur.lyrics;
  } catch (e) {}
  return "";
}
// v5 账号体系统一：旧单密码网关认证(/api/v1/auth/login|logout|check)已移除。
// App 认证统一走 /api/v1/accounts/*（用户名+密码，见 routes/appsvc.js）。
export function registerGateway(app) {
  // ===== 1. 搜索（全网多平台并发）=====
  app.get("/api/v1/search", async (req, reply) => {
    var q = String(req.query.q || req.query.keyword || "").trim();
    if (!q) return err(reply, 400, "缺少 q");
    var page = Number(req.query.page) || 1;
    var limit = Math.min(Number(req.query.limit) || 20, 50);
    var plats = req.query.platforms ? String(req.query.platforms).split(",").filter(function (x) { return PLATS.indexOf(x) >= 0; }) : PLATS;
    if (!plats.length) plats = PLATS;
    var results = await Promise.all(plats.map(function (pl) {
      return new Promise(function (res) {
        platSearch(pl, q, page, limit).then(function (r) {
          res({ platform: pl, name: PLATFORM_NAMES[pl], ok: true, count: (r.list || []).length, songs: r.list || [] });
        }).catch(function (e) {
          res({ platform: pl, name: PLATFORM_NAMES[pl], ok: false, error: String(e.message || e).slice(0, 120), count: 0, songs: [] });
        });
      });
    }));
    return { query: q, page: page, results: results };
  });

  // ===== 2. 下载（触发完整管线）=====
  app.post("/api/v1/download", async (req, reply) => {
    var body = req.body || {};
    var songs = body.songs || body.items || [];
    songs = (Array.isArray(songs) ? songs : []).filter(function (x) { return x && (x.id || x.songmid); });
    if (!songs.length) return err(reply, 400, "songs 为空（把 /api/v1/search 返回的 songs 元素原样传入）");
    var quality = ["auto", "flac", "320k", "128k"].indexOf(String(body.quality)) >= 0 ? String(body.quality) : String(cfg("default_quality", "auto"));
    var ids = songs.map(function (s) { return enqueueDownload(s, quality, String(body.source_id || "")); });
    return { ok: true, taskIds: ids, quality: quality };
  });
  app.get("/api/v1/tasks", async (req) => {
    var rows = getDb().prepare("SELECT * FROM tasks ORDER BY created_at DESC LIMIT 100").all();
    return { list: rows.map(function (t) {
      var p = {}; try { p = JSON.parse(t.payload || "{}"); } catch (e) {}
      return { id: t.id, status: t.status, progress: t.progress, message: t.message, error: t.error, track_id: t.track_id, quality: p.quality, songName: (p.song || {}).name, songSource: (p.song || {}).source };
    }) };
  });

  // ===== 3. 曲库 =====
  app.get("/api/v1/library", async (req) => {
    var q = String(req.query.q || "");
    var offset = Number(req.query.offset) || 0;
    var limit = Math.min(Number(req.query.limit) || 200, 500);
    var rows = getDb().prepare("SELECT * FROM tracks ORDER BY updated_at DESC").all();
    if (q) { var qq = q.toLowerCase(); rows = rows.filter(function (r) { return (String(r.title) + String(r.artist) + String(r.album)).toLowerCase().includes(qq); }); }
    var total = rows.length;
    rows = rows.slice(offset, offset + limit);
    return { total: total, offset: offset, limit: limit, tracks: rows.map(trackBrief) };
  });
  app.get("/api/v1/library/:id", async (req, reply) => {
    var r = getDb().prepare("SELECT * FROM tracks WHERE id=?").get(req.params.id);
    if (!r) return err(reply, 404, "不存在");
    var out = trackBrief(r); out.meta = {};
    try { var e2 = JSON.parse(r.extra || "{}"); out.meta = e2.meta || {}; } catch (x) {}
    return out;
  });
  app.get("/api/v1/lyrics/:id", async (req, reply) => {
    var r = getDb().prepare("SELECT * FROM tracks WHERE id=?").get(req.params.id);
    if (!r) return err(reply, 404, "不存在");
    var text = await lrcText(r);
    return { id: r.id, title: r.title, artist: r.artist, synced: /\[\d\d:/.test(text), lyrics: text, lines: text ? parseLrc(text) : [] };
  });
  app.get("/api/v1/cover/:id", async (req, reply) => {
    var r = getDb().prepare("SELECT * FROM tracks WHERE id=?").get(req.params.id);
    if (!r) return err(reply, 404, "不存在");
    // 1) 网盘同名 .jpg 封面 sidecar（与歌词 .lrc 调用方式一致：网盘文件优先，缓存 10 分钟）
    var scKey = r.id + "-sidecar";
    var sc = coverCache.get(scKey);
    if (r.remote_path && (!sc || Date.now() - sc.ts > 600000)) {
      sc = null;
      try {
        var imgPath = davPath(String(r.remote_path).replace(/\.[a-z0-9]+$/i, "") + ".jpg");
        var raw = await getClient().getFileContents(imgPath);
        var b = Buffer.isBuffer(raw) ? raw : Buffer.from(raw);
        if (b.length > 64) sc = { buf: b, type: sniffImageType(b) || "image/jpeg", ts: Date.now() };
      } catch (e) { sc = null; }
      // 负结果也缓存，避免无封面曲目每次展示都打网盘
      coverCache.set(scKey, sc || { buf: null, type: "", ts: Date.now() });
      sc = coverCache.get(scKey);
    }
    if (sc && sc.buf) {
      reply.header("Cache-Control", "public, max-age=3600");
      return reply.type(sc.type).send(sc.buf);
    }
    // 2) Navidrome 内嵌封面（回退链路，原有逻辑）
    var size = Math.min(Number(req.query.size) || 300, 1200);
    var key = r.id + "-" + size;
    var c = coverCache.get(key);
    if (!c || Date.now() - c.ts > 600000) {
      var nd = await ndOf(r);
      if (!nd.coverArt) return err(reply, 404, "无封面");
      try { c = await ndFetchCover(nd.coverArt, size); } catch (e) { return err(reply, 502, "封面获取失败"); }
      coverCache.set(key, c);
    }
    reply.header("Cache-Control", "public, max-age=3600");
    return reply.type(c.type).send(c.buf);
  });

  // ===== 4. 播放流 =====
  app.get("/api/v1/stream/:id", async (req, reply) => {
    var r = getDb().prepare("SELECT * FROM tracks WHERE id=?").get(req.params.id);
    if (!r || !r.remote_path) return err(reply, 404, "不存在");
    var ext = String(r.remote_path).split(".").pop().toLowerCase();
    var mime = ext === "flac" ? "audio/flac" : ext === "ogg" ? "audio/ogg" : ext === "m4a" ? "audio/mp4" : ext === "wav" ? "audio/wav" : "audio/mpeg";
    const cached = await cachedAudio(r);
    if (cached) {
      const stat = (await import("node:fs/promises")).stat;
      const size = (await stat(cached)).size;
      let start = 0, end = size - 1, status = 200;
      const match = String(req.headers.range || "").match(/^bytes=(\d*)-(\d*)$/);
      if (match) {
        if (!match[1] && match[2]) { start = Math.max(0, size - Number(match[2])); }
        else { start = Number(match[1] || 0); end = match[2] ? Math.min(Number(match[2]), size - 1) : size - 1; }
        if (!Number.isFinite(start) || start >= size || start > end) {
          reply.code(416).header("Content-Range", "bytes */" + size); return reply.send();
        }
        status = 206;
      }
      reply.code(status).header("Content-Type", mime).header("Accept-Ranges", "bytes")
        .header("Content-Length", end - start + 1).header("X-Audio-Cache", "HIT")
        .header("Cache-Control", "private, max-age=86400");
      if (status === 206) reply.header("Content-Range", "bytes " + start + "-" + end + "/" + size);
      return reply.send((await import("node:fs")).createReadStream(cached, { start, end }));
    }
    var base = String(cfg("webdav_url", "")).replace(/\/+$/, "").replace(/\/webdav$/i, "/webdav/");
    var parts = davPath(r.remote_path).split("/").map(function (s) { return encodeURIComponent(s); }).join("/");
    var url = base + "/" + parts.replace(/^\/+/, "");
    var auth = "Basic " + Buffer.from(String(cfg("webdav_user", "")) + ":" + String(cfg("webdav_pass", ""))).toString("base64");
    var headers = { Authorization: auth };
    if (req.headers.range) headers.Range = req.headers.range;
    var up;
    try { up = await fetch(url, { headers: headers, redirect: "follow" }); }
    catch (e) { return err(reply, 502, "网盘不可达: " + String(e.message || e).slice(0, 80)); }
    if (!up.ok && up.status !== 206) return err(reply, 502, "网盘返回 " + up.status);
    // 首次播放透传 Range，后台独立完整缓存；客户端断开不阻塞缓存任务。
    const rangeOnly = /^bytes=\d+-\d+$/.test(String(req.headers.range || ""));
    if (!rangeOnly) prefetchAudio(r);
    reply.code(up.status === 206 ? 206 : 200);
    reply.header("Content-Type", mime);
    reply.header("Accept-Ranges", "bytes");
    reply.header("Cache-Control", "no-store");
    reply.header("X-Audio-Cache", "MISS");
    var cl = up.headers.get("content-length"); if (cl) reply.header("Content-Length", cl);
    var cr = up.headers.get("content-range"); if (cr) reply.header("Content-Range", cr);
    const { Readable } = await import("node:stream");
    return reply.send(Readable.fromWeb(up.body));
  });

  app.get("/api/v1/audio-cache", async () => audioCacheStats());

  // ===== 分享链接订阅 =====
  // 1) 解析并生成预览（不下载）：{url, quality?, share_user?}
  app.post("/api/v1/shares", async (req, reply) => {
    try {
      var b = req.body || {};
      return await prepareShareLink(String(b.url || ""), b.quality ? String(b.quality) : "", b.share_user ? String(b.share_user) : "");
    } catch (e) { return err(reply, 400, e.message || String(e)); }
  });
  // 2) 预览详情（分享用户/曲目清单/在库标记）
  app.get("/api/v1/shares/:id/preview", async (req, reply) => {
    try { return getSharePreview(req.params.id); }
    catch (e) { return err(reply, 404, e.message || String(e)); }
  });
  // 3) 确认下载（可改 share_user / quality / keys 勾选）
  app.post("/api/v1/shares/:id/commit", async (req, reply) => {
    try {
      var b = req.body || {};
      return await commitShareLink(req.params.id, { share_user: b.share_user ? String(b.share_user) : "", quality: b.quality ? String(b.quality) : "", keys: Array.isArray(b.keys) ? b.keys : null });
    } catch (e) { return err(reply, 400, e.message || String(e)); }
  });
  // 批量：删除曲目 / 取消任务 / 删除任务记录
  app.post("/api/v1/tracks/bulk-delete", async (req, reply) => {
    var ids = (req.body && req.body.ids) || [];
    if (!ids.length) return err(reply, 400, "ids 为空");
    var del = 0, errs = [];
    for (var id of ids) {
      try {
        var t = getDb().prepare("SELECT * FROM tracks WHERE id=?").get(id);
        if (!t) continue;
        try { if (t.remote_path) await delRemote(t.remote_path); } catch (e) { errs.push(t.title); }
        try { if (t.remote_path) await delRemote(t.remote_path.replace(/\.[a-z0-9]+$/i, "") + ".lrc"); } catch (e) {}
        try { if (t.remote_path) await delRemote(t.remote_path.replace(/\.[a-z0-9]+$/i, "") + ".jpg"); } catch (e) {}
        getDb().prepare("DELETE FROM tracks WHERE id=?").run(id);
        del++;
      } catch (e) {}
    }
    try { await refreshShare(); } catch (e) {}
    return { ok: true, deleted: del, errors: errs.slice(0, 5) };
  });
  app.post("/api/v1/tasks/bulk-cancel", async (req, reply) => {
    var ids = (req.body && req.body.ids) || [];
    if (!ids.length) return err(reply, 400, "ids 为空");
    var n = 0;
    for (var id of ids) {
      var r = getDb().prepare("UPDATE tasks SET status='cancelled', updated_at=? WHERE id=? AND status IN ('pending','queued')").run(Date.now(), id);
      if (r.changes) { n++; continue; }
      try { cancelShareTask(id); n++; } catch (e) { var r2 = getDb().prepare("UPDATE tasks SET status='cancelled', updated_at=? WHERE id=? AND status NOT IN ('done','cancelled')").run(Date.now(), id); if (r2.changes) n++; }
    }
    return { ok: true, cancelled: n };
  });
  app.post("/api/v1/tasks/bulk-delete", async (req, reply) => {
    var ids = (req.body && req.body.ids) || [];
    if (!ids.length) return err(reply, 400, "ids 为空");
    var del = 0;
    for (var id of ids) {
      var s0 = getDb().prepare("SELECT status FROM tasks WHERE id=?").get(id);
      if (s0 && ["done", "failed", "cancelled"].includes(s0.status)) { getDb().prepare("DELETE FROM tasks WHERE id=?").run(id); del++; }
    }
    return { ok: true, deleted: del };
  });
  app.get("/api/v1/shares", async (req) => {
    var rows = getDb().prepare("SELECT * FROM sharelinks ORDER BY added_at DESC").all();
    var list = rows.map(function (l) {
      var inlib = getDb().prepare("SELECT COUNT(*) c FROM sharelink_items WHERE link_id=? AND (state='done' OR state='exists')").get(l.id).c;
      return { id: l.id, url: l.url, platform: l.platform, title: l.title, share_user: l.share_user, quality: l.quality, status: l.status, song_count: l.song_count, in_library: inlib, added_at: l.added_at, last_sync_at: l.last_sync_at, last_result: l.last_result };
    });
    return { list: list };
  });
  app.post("/api/v1/shares/:id/sync", async (req, reply) => {
    try { return await syncShareLink(req.params.id, { force: !!(req.body && req.body.force) }); }
    catch (e) { return err(reply, 500, e.message || String(e)); }
  });
  app.delete("/api/v1/shares/:id", async (req) => {
    var rows = getDb().prepare("SELECT task_id FROM sharelink_items WHERE link_id=?").all(req.params.id);
    var pend = getDb().prepare("UPDATE tasks SET status='cancelled', updated_at=? WHERE id=? AND status IN ('pending','queued')");
    for (var x of rows) if (x.task_id) pend.run(Date.now(), x.task_id);
    getDb().prepare("DELETE FROM sharelink_items WHERE link_id=?").run(req.params.id);
    getDb().prepare("DELETE FROM sharelinks WHERE id=?").run(req.params.id);
    return { ok: true };
  });
  app.get("/api/v1/shares/:id/songs", async (req) => {
    var rows = getDb().prepare("SELECT no,title,artist,task_id,track_id,state,added_at FROM sharelink_items WHERE link_id=? ORDER BY no").all(req.params.id);
    return { list: rows };
  });

  // ===== 系统摘要 / 触发扫描 =====
  app.get("/api/v1/system", async () => {
    var counts = {};
    try {
      counts.tracks = getDb().prepare("SELECT COUNT(*) c FROM tracks").get().c;
      counts.queue = getDb().prepare("SELECT COUNT(*) c FROM tasks WHERE status != 'done' AND status != 'failed'").get().c;
      counts.imported = getDb().prepare("SELECT COUNT(*) c FROM tracks WHERE status='imported'").get().c;
    } catch (e) {}
    var st = { ok: true, api: "v1", counts: counts };
    try { st.scan = await scanStatus(); } catch (e) {}
    return st;
  });
  app.post("/api/v1/scan", async () => await startScan());
}

async function ndFetchCover(coverArtId, size) {
  var jwt = await getJwt();
  var u = String(cfg("navidrome_url", "http://navidrome:4533")).replace(/\/+$/, "") + "/rest/getCoverArt.view?u=" + encodeURIComponent(String(cfg("nav_user", ""))) + "&v=1.16.1&c=musicpipe&jwt=" + jwt + "&id=" + encodeURIComponent(coverArtId) + "&size=" + size;
  var r = await fetch(u, { signal: AbortSignal.timeout(15000) });
  if (!r.ok) throw new Error("cover HTTP " + r.status);
  var buf = Buffer.from(await r.arrayBuffer());
  return { buf: buf, type: r.headers.get("content-type") || "image/jpeg", ts: Date.now() };
}
