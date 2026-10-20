import { getDb, logEvent } from "./db/index.js";
import { cfg } from "./config.js";
import { fetchWithTimeout } from "./util.js";
import { search as platSearch, PLATFORM_NAMES } from "./sources/search.js";
import { enqueueDownload } from "./downloader/queue.js";
import { navRequest, startScan, scanStatus, getJwt } from "./pipeline/navidrome.js";
import { prepareShareLink, getSharePreview, commitShareLink, syncShareLink } from "./pipeline/sharelink.js";
import { getClient, davPath, putBuffer, deleteRemote as delRemote } from "./webdav/client.js";
import { refreshDir as refreshShare } from "./webdav/rclone.js";
import { cancelTask as cancelShareTask } from "./downloader/queue.js";
import { sniffImageType, fetchCoverBuf } from "./downloader/cover.js";
import { lookupCoverUrl } from "./downloader/coverlookup.js";

const PLATS = ["kw", "kg", "tx", "wy"];
var ndMap = new Map();      // trackId -> {ndSongId, coverArt, ts}
var coverCache = new Map(); // key -> {buf, type, ts}

function err(reply, code, msg) { reply.code(code); return { error: msg }; }

/**
 * v1.9.1 封面自愈：网盘没有 .jpg sidecar、Navidrome 也没有 coverArt 时，
 * 按"歌名+歌手"在线反查一次并立刻写成 sidecar（下次直接命中，不必等重新下载）。
 * 只做 best-effort：任何失败都返回 null，不影响 404 语义，也绝不影响播放。
 */
async function lazyCover(r) {
  if (!r || !r.remote_path) return null;
  var url = null;
  try { url = await lookupCoverUrl(r.title, r.artist); } catch (e) { url = null; }
  if (!url) return null;
  var buf = await fetchCoverBuf(url);
  if (!buf) return null;
  var type = sniffImageType(buf) || "image/jpeg";
  try { await putBuffer(String(r.remote_path).replace(/\.[a-z0-9]+$/i, "") + ".jpg", buf); } catch (e) {}
  coverCache.set(r.id + "-sidecar", { buf: buf, type: type, ts: Date.now() });
  try { logEvent("cover_heal", (r.title || "") + " <- " + String(url).slice(0, 80)); } catch (e) {}
  return { buf: buf, type: type };
}

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

// ===== 歌词读取（v1.5.0 重写：内存 LRU + 两级并发 + 短超时） =====
// 旧实现串行三级回退且零缓存，命中侧车也要 287-836ms；第三级 mtScrapeRead 在
// musictag.js 中并不存在（每次必抛 TypeError 被空 catch 吞掉），属死代码已删除。
var lrcCache = new Map();     // trackId -> { text, ts }（含空结果，负缓存）
var LRC_TTL = 3600000;        // 正结果 1 小时
var LRC_NEG_TTL = 300000;     // 空结果 5 分钟，避免无歌词曲反复回源
var LRC_MAX = 500;            // LRU 上限
var LRC_TIER_MS = 8000;       // 单级超时（WebDAV 客户端自带 120s，必须自行截断）

function lrcCacheGet(k) {
  var hit = lrcCache.get(k);
  if (!hit) return undefined;
  var ttl = hit.text ? LRC_TTL : LRC_NEG_TTL;
  if (Date.now() - hit.ts > ttl) { lrcCache.delete(k); return undefined; }
  return hit.text;
}

function lrcCacheSet(k, text) {
  lrcCache.delete(k);
  lrcCache.set(k, { text: text, ts: Date.now() });
  while (lrcCache.size > LRC_MAX) {
    var oldest = lrcCache.keys().next();
    if (oldest.done) break;
    lrcCache.delete(oldest.value);
  }
}

/** 从 tracks.extra 里取持久化的歌词文本（下载/刮削时写入，命中即 0ms）。 */
function lrcFromDb(track) {
  try {
    var extra = JSON.parse(track.extra || "{}");
    var lrc = extra && extra.lrc;
    return typeof lrc === "string" && lrc.length > 0 ? lrc : "";
  } catch (e) {
    return "";
  }
}

/** 把歌词文本持久化进 tracks.extra（不改表结构），失败静默。 */
function lrcPersist(trackId, text) {
  if (!text) return;
  try {
    var row = getDb().prepare("SELECT extra FROM tracks WHERE id=?").get(trackId);
    if (!row) return;
    var extra = {};
    try { extra = JSON.parse(row.extra || "{}"); } catch (e) { extra = {}; }
    if (extra.lrc === text) return;
    extra.lrc = text;
    getDb().prepare("UPDATE tracks SET extra=? WHERE id=?").run(JSON.stringify(extra), trackId);
  } catch (e) {}
}

// v1.5.0 歌词预热：把全库歌词在后台逐首抓一遍并持久化到 tracks.extra。
// 冷路径每首约 400-700ms（123pan WebDAV getFileContents 的固有延迟），但**每首只需付一次**：
// 写进 DB 后无论容器怎么重启都是 2ms。放在启动后延迟执行、串行限速，避免开机就把
// 网盘 API 打爆或与下载任务抢带宽。可用 settings.lyrics_prewarm=0 关闭。
var LRC_WARM_DELAY_MS = 30000;   // 启动后等 30s，先让服务把冷启动的其它事做完
var LRC_WARM_GAP_MS = 400;       // 每首之间间隔，串行慢跑
var lrcWarmRunning = false;

async function lrcPrewarm() {
  if (lrcWarmRunning) return;
  if (String(cfg("lyrics_prewarm", "1")) === "0") return;
  lrcWarmRunning = true;
  try {
    var rows = getDb().prepare(
      "SELECT * FROM tracks WHERE remote_path IS NOT NULL AND remote_path <> '' AND status <> 'failed' ORDER BY created_at DESC"
    ).all();
    var done = 0, hit = 0, miss = 0;
    for (var i = 0; i < rows.length; i++) {
      var t = rows[i];
      // 已有持久化结果（含之前抓过但确实没有歌词的曲目）直接跳过
      var extra = {};
      try { extra = JSON.parse(t.extra || "{}"); } catch (e) { extra = {}; }
      if (typeof extra.lrc === "string" && extra.lrc !== "") { done++; continue; }
      if (extra.lrcNone) { done++; continue; }
      try {
        var text = await lrcText(t);
        if (text) hit++; else miss++;
        // 没有歌词也要落一个空标记，避免下次预热再抓一遍
        if (!text) {
          try {
            extra.lrcNone = 1;
            getDb().prepare("UPDATE tracks SET extra=? WHERE id=?").run(JSON.stringify(extra), t.id);
          } catch (e2) {}
        }
      } catch (e3) {}
      done++;
      await new Promise(function (r) { setTimeout(r, LRC_WARM_GAP_MS); });
    }
    logEvent("lyrics_prewarm", "warm done=" + done + " hit=" + hit + " miss=" + miss);
    console.log("[lrc-warm] done=" + done + " hit=" + hit + " miss=" + miss);
  } catch (e) {
    console.log("[lrc-warm] failed: " + (e && e.message));
  } finally {
    lrcWarmRunning = false;
  }
}

setTimeout(function () { lrcPrewarm(); }, LRC_WARM_DELAY_MS);

/** 给任意 promise 加超时；超时或异常返回 fallback，绝不阻塞整体。 */
function withTimeout(p, ms, fallback) {
  return new Promise(function (resolve) {
    var settled = false;
    var timer = setTimeout(function () {
      if (settled) return;
      settled = true;
      resolve(fallback);
    }, ms);
    Promise.resolve(p).then(
      function (v) { if (settled) return; settled = true; clearTimeout(timer); resolve(v); },
      function () { if (settled) return; settled = true; clearTimeout(timer); resolve(fallback); }
    );
  });
}

/** 第一级：网盘同名 .lrc 侧车。 */
function lrcFromSidecar(track) {
  try {
    var lrcPath = davPath(String(track.remote_path).replace(/\.[a-z0-9]+$/i, "") + ".lrc");
    return Promise.resolve(getClient().getFileContents(lrcPath)).then(function (buf) {
      var s = typeof buf === "string" ? buf : String(buf);
      return s && /\[\d\d:/.test(s) ? s : "";
    });
  } catch (e) {
    return Promise.resolve("");
  }
}

/** 第二级：Navidrome 结构化歌词。 */
function lrcFromNavidrome(track) {
  return Promise.resolve(ndOf(track)).then(function (nd) {
    if (!nd || !nd.ndSongId) return "";
    return Promise.resolve(navRequest("getLyricsBySongId", "id=" + nd.ndSongId)).then(function (resp) {
      var list = (((resp.lyricsBySongId || {}).lyricsList || {}).structuredLyrics || {}).entry || [];
      if (!list.length) return "";
      return list.map(function (e2) { return (e2.line || []).join("\n"); }).join("\n");
    });
  });
}

async function lrcText(track) {
  if (!track || !track.id) return "";
  // 0) 内存 LRU → 1) DB 持久化（下载/刮削期写入）→ 2) 网盘侧车 / Navidrome 并发
  var cached = lrcCacheGet(track.id);
  if (cached !== undefined) return cached;
  var persisted = lrcFromDb(track);
  if (persisted) { lrcCacheSet(track.id, persisted); return persisted; }

  // 两级并发：谁先给出非空结果就用谁（侧车仍优先，但不再等它超时后才问 Navidrome）
  var text = await new Promise(function (resolve) {
    var done = false;
    var pending = 2;
    function settle(v) {
      if (done) return;
      if (v) { done = true; resolve(v); return; }
      pending -= 1;
      if (pending <= 0) { done = true; resolve(""); }
    }
    withTimeout(lrcFromSidecar(track), LRC_TIER_MS, "").then(settle);
    withTimeout(lrcFromNavidrome(track), LRC_TIER_MS, "").then(settle);
    setTimeout(function () { if (!done) { done = true; resolve(""); } }, LRC_TIER_MS + 500);
  });

  lrcCacheSet(track.id, text || "");
  if (text) lrcPersist(track.id, text);
  return text || "";
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
      if (!nd.coverArt) {
        // 3) v1.9.1 自愈：两张来源都空时在线反查并落 sidecar（老歌无需重新下载）
        var healed = await lazyCover(r);
        if (healed) {
          reply.header("Cache-Control", "public, max-age=3600");
          return reply.type(healed.type).send(healed.buf);
        }
        return err(reply, 404, "无封面");
      }
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
    var base = String(cfg("webdav_url", "")).replace(/\/+$/, "");
    var parts = davPath(r.remote_path).split("/").map(function (s) { return encodeURIComponent(s); }).join("/");
    var url = base + parts;
    var auth = "Basic " + Buffer.from(String(cfg("webdav_user", "")) + ":" + String(cfg("webdav_pass", ""))).toString("base64");
    var headers = { Authorization: auth };
    if (req.headers.range) headers.Range = req.headers.range;
    var up;
    try { up = await fetch(url, { headers: headers, redirect: "follow" }); }
    catch (e) { return err(reply, 502, "网盘不可达: " + String(e.message || e).slice(0, 80)); }
    if (!up.ok && up.status !== 206) return err(reply, 502, "网盘返回 " + up.status);
    reply.code(up.status === 206 ? 206 : 200);
    reply.header("Content-Type", mime);
    reply.header("Accept-Ranges", "bytes");
    reply.header("Cache-Control", "no-store");
    var cl = up.headers.get("content-length"); if (cl) reply.header("Content-Length", cl);
    var cr = up.headers.get("content-range"); if (cr) reply.header("Content-Range", cr);
    const { Readable } = await import("node:stream");
    return reply.send(Readable.fromWeb(up.body));
  });

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

  // ===== v1.6.0: 歌单管理（合并）=====
  // 合并语义：把 source 歌单的条目并入 target，按 (title, artist) 归一化去重；
  // source 歌单随后删除（只删分享记录，曲库里的歌曲一首不动）。
  // 冲突处理：target 已有同名同歌手条目时保留 target 的（它可能已下载完成），
  // 但若 target 那条不完整（track_id 空）而 source 那条已入库，则把 track_id 继承过来。
  app.post("/api/v1/shares/:id/merge", async (req, reply) => {
    var target = req.params.id;
    var body = req.body || {};
    var source = body.source_id ? String(body.source_id) : "";
    if (!source) return err(reply, 400, "source_id 为空");
    if (source === target) return err(reply, 400, "不能把歌单合并到它自己");
    var db = getDb();
    var tgt = db.prepare("SELECT * FROM sharelinks WHERE id=?").get(target);
    var src = db.prepare("SELECT * FROM sharelinks WHERE id=?").get(source);
    if (!tgt) return err(reply, 404, "目标歌单不存在");
    if (!src) return err(reply, 404, "被合并的歌单不存在");

    var norm = function (s) {
      return String(s == null ? "" : s).trim().toLowerCase().replace(/\s+/g, "");
    };
    var tgtRows = db.prepare("SELECT * FROM sharelink_items WHERE link_id=? ORDER BY no").all(target);
    var srcRows = db.prepare("SELECT * FROM sharelink_items WHERE link_id=? ORDER BY no").all(source);

    var byKey = new Map();
    for (var r of tgtRows) byKey.set(norm(r.title) + "|" + norm(r.artist), r);
    var hasTrack = function (r) { return !!(r.track_id && String(r.track_id).trim()); };
    var isDone = function (r) { return r.state === "done" || r.state === "exists"; };

    var nextNo = tgtRows.reduce(function (m, r) { return Math.max(m, Number(r.no) || 0); }, 0);
    var inserted = 0, skipped = 0, inherited = 0;
    var ins = db.prepare("INSERT INTO sharelink_items(link_id,no,ext_key,title,artist,state,task_id,track_id,added_at) VALUES(?,?,?,?,?,?,?,?,?)");
    var updTrack = db.prepare("UPDATE sharelink_items SET track_id=?, state='done' WHERE link_id=? AND no=?");
    var detach = db.prepare("UPDATE sharelink_items SET track_id=NULL, state='queued' WHERE link_id=? AND no=?");

    for (var s of srcRows) {
      var k = norm(s.title) + "|" + norm(s.artist);
      var hit = byKey.get(k);
      if (hit) {
        if (!hasTrack(hit) && hasTrack(s) && isDone(s)) {
          // 目标那条还没入库，但被合并那条已入库 —— 继承曲库引用，避免重复下载
          updTrack.run(s.track_id, target, hit.no);
          hit.track_id = s.track_id; hit.state = "done";
          inherited++;
        } else if (hasTrack(hit) && !hasTrack(s) && isDone(s) && isDone(hit)) {
          // 两者都已入库但源那条丢失了 track_id —— 不动，计数说明即可
        }
        skipped++;
        continue;
      }
      nextNo++;
      ins.run(target, nextNo, s.ext_key || "", s.title, s.artist, s.state === "done" || s.state === "exists" ? "exists" : "queued",
        null, hasTrack(s) ? s.track_id : null, Date.now());
      byKey.set(k, { no: nextNo, title: s.title, artist: s.artist, track_id: s.track_id, state: s.state });
      inserted++;
    }

    // 目标歌单统计刷新
    var cnt = db.prepare("SELECT COUNT(*) c FROM sharelink_items WHERE link_id=?").get(target).c;
    db.prepare("UPDATE sharelinks SET song_count=?, last_sync_at=? WHERE id=?").run(cnt, Date.now(), target);

    // 源歌单：取消未完成任务并删除分享记录（曲库不动）
    var srcTasks = db.prepare("SELECT task_id FROM sharelink_items WHERE link_id=?").all(source);
    var pend = db.prepare("UPDATE tasks SET status='cancelled', updated_at=? WHERE id=? AND status IN ('pending','queued')");
    for (var x of srcTasks) if (x.task_id) pend.run(Date.now(), x.task_id);
    db.prepare("DELETE FROM sharelink_items WHERE link_id=?").run(source);
    db.prepare("DELETE FROM sharelinks WHERE id=?").run(source);
    try { logEvent("share_merge", tgt.title + " <- " + src.title + " inserted=" + inserted + " skipped=" + skipped); } catch (e) {}

    return { ok: true, target_id: target, source_id: source, inserted: inserted, skipped: skipped, inherited: inherited, total: cnt };
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