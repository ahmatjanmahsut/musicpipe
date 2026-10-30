import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import { getDb, getSetting, setSetting, allSettings, logEvent } from "../db/index.js";
import { ENV, cfg } from "../config.js";
import { newId } from "../util.js";
import { listSources, addSource, removeSource, setEnabled, getBox, activeBoxes, boxForPlatform, hasAnyBox } from "../sources/registry.js";
import { search as platSearch, PLATFORMS, PLATFORM_NAMES } from "../sources/search.js";
import { enqueueDownload, retryTask, cancelTask, clearFinished, poolSize } from "../downloader/queue.js";
import { organizeTrack, organizeAllPending, computeTargetPath } from "../pipeline/organizer.js";
import { mtProbe, mtScrapeTrack, mtLogin } from "../pipeline/musictag.js";
import { Readable } from "node:stream";
import { navPing, startScan, scanStatus } from "../pipeline/navidrome.js";
import { rcloneStatus, refreshDir } from "../webdav/rclone.js";
import { testConnection, listRemote, deleteRemote, webdavReady, getClient, davPath, statRemote } from "../webdav/client.js";
import { createSession, destroySession, sessionUser, hasAnyUser } from "../auth.js";
import { addShareLink, prepareShareLink, getSharePreview, commitShareLink, syncShareLink, fetchPlaylist, parseShareUrl } from "../pipeline/sharelink.js";

function err(reply, code, msg) { reply.code(code); return { error: String(msg && msg.message || msg || "error") }; }

// v5 账号体系统一：控制台登录 = users 表（用户名+密码，仅 admin 角色可进入控制台）。
// 与 App /api/v1/accounts/* 共用同一账号体系；错误码与 appsvc 对齐(user_not_found/wrong_password)。
var CONSOLE_USERNAME_RE = /^[A-Za-z0-9_\u4e00-\u9fa5]{3,32}$/;
var consoleLoginFails = new Map(); // ip -> {n, ts}

export function registerRoutes(app, broadcast) {
  // ===== auth（控制台，仅管理员）=====
  app.post("/api/auth/setup", async (req, reply) => {
    if (hasAnyUser()) return err(reply, 400, "已有账号，请直接登录");
    var b = req.body || {};
    var username = String(b.username || "").trim();
    var password = String(b.password || "");
    if (!CONSOLE_USERNAME_RE.test(username)) return err(reply, 400, "invalid_username");
    if (password.length < 6) return err(reply, 400, "invalid_password");
    var { hashPassword } = await import("../util.js");
    var now = Date.now();
    var r = getDb().prepare("INSERT INTO users(username,password_hash,role,created_at) VALUES(?,?,?,?)").run(username, hashPassword(password), "admin", now);
    logEvent("console_setup", username + " (admin)");
    var token = createSession(Number(r.lastInsertRowid));
    reply.cookie("mp_session", token, { path: "/", httpOnly: true, maxAge: 2592000 });
    return { ok: true, user: { id: Number(r.lastInsertRowid), username: username, role: "admin" } };
  });
  app.post("/api/auth/login", async (req, reply) => {
    var ip = (req.headers["x-forwarded-for"] || req.ip || "?").toString().split(",")[0].trim();
    var f = consoleLoginFails.get(ip) || { n: 0, ts: Date.now() };
    if (Date.now() - f.ts > 900000) { f.n = 0; f.ts = Date.now(); }
    if (f.n >= 20) return err(reply, 429, "尝试过多，15分钟后重试");
    var b = req.body || {};
    var username = String(b.username || "").trim();
    var password = String(b.password || "");
    var { verifyPassword } = await import("../util.js");
    var u = getDb().prepare("SELECT * FROM users WHERE username=?").get(username);
    if (!u) { f.n++; consoleLoginFails.set(ip, f); return err(reply, 404, "user_not_found"); }
    if (!verifyPassword(password, u.password_hash)) { f.n++; consoleLoginFails.set(ip, f); return err(reply, 401, "wrong_password"); }
    if (u.role !== "admin") return err(reply, 403, "admin_only"); // 非管理员禁止登录控制台（App 功能不受影响）
    consoleLoginFails.delete(ip);
    var token = createSession(u.id);
    logEvent("console_login", u.username);
    reply.cookie("mp_session", token, { path: "/", httpOnly: true, maxAge: 2592000 });
    return { ok: true, user: { id: u.id, username: u.username, role: u.role } };
  });
  app.post("/api/auth/logout", async (req, reply) => {
    destroySession(req.cookies ? req.cookies.mp_session : null);
    reply.clearCookie("mp_session", { path: "/" });
    return { ok: true };
  });
  app.get("/api/auth/status", async (req) => {
    var u = sessionUser(req);
    return { needsSetup: !hasAnyUser(), loggedIn: !!u, username: u ? u.username : null, role: u ? u.role : null };
  });

  // ===== sources =====
  app.get("/api/sources", async () => ({ list: listSources() }));
  app.post("/api/sources", async (req, reply) => {
    try {
      var name = "", code = "";
      if (req.body && req.body.code) { name = req.body.name || "源"; code = req.body.code; }
      else if (req.isMultipart && req.isMultipart()) {
        for await (var part of req.parts()) {
          if (part.type === "file") code = await part.toBuffer().then(function (b) { return b.toString("utf8"); });
          else if (part.fieldname === "name") name = String(part.value || "");
        }
      }
      if (!code || code.length < 50) return err(reply, 400, "源文件为空或过小");
      if (code.length > 2 * 1024 * 1024) return err(reply, 400, "源文件超过2MB");
      var r = await addSource(name || "未命名源", code);
      logEvent("source_add", r.name);
      return r;
    } catch (e) { return err(reply, 400, e); }
  });
  app.post("/api/sources/:id/enable", async (req) => { return await setEnabled(req.params.id, true); });
  app.post("/api/sources/:id/disable", async (req) => { setEnabled(req.params.id, false); return { ok: true }; });
  app.delete("/api/sources/:id", async (req) => { removeSource(req.params.id); return { ok: true }; });
  app.post("/api/sources/:id/test", async (req, reply) => {
    var box = getBox(req.params.id);
    if (!box) return err(reply, 400, "源未加载");
    var kw = (req.body && req.body.keyword) || "晴天";
    var plats = box.platforms().filter(function (x) { return PLATFORMS.indexOf(x) >= 0; });
    if (!plats.length) plats = PLATFORMS.slice();
    var out = { ok: false, steps: [] };
    for (var plat of plats) {
      var step = { platform: plat, search: "fail", url: "skip", quality: "" };
      try {
        var res = await platSearch(plat, kw, 1, 5);
        step.search = res.list.length + "条";
        if (res.list.length && box.supports(plat)) {
          var song = res.list[0];
          try {
            var got = await getPlayUrlBox(box, plat, song);
            step.url = "ok"; step.quality = got.quality;
            out.ok = true;
          } catch (e) { step.url = String(e.message || e).slice(0, 80); }
        } else { step.url = box.supports(plat) ? "no" : "平台未声明"; }
      } catch (e) { step.search = String(e.message || e).slice(0, 80); }
      out.steps.push(step);
      if (out.ok) break;
    }
    if (!out.ok) return err(reply, 502, "自测失败: " + JSON.stringify(out.steps).slice(0, 400));
    return out;
  });
  async function getPlayUrlBox(box, plat, song) {
    var { getPlayUrlWithFallback } = await import("../sources/adapter.js");
    return getPlayUrlWithFallback(box, plat, song, String(cfg("default_quality", "320k")));
  }

  // ===== search =====
  app.get("/api/search", async (req, reply) => {
    var keyword = String(req.query.keyword || "").trim();
    if (!keyword) return err(reply, 400, "缺少 keyword");
    var page = Number(req.query.page) || 1;
    var limit = Math.min(Number(req.query.limit) || 30, 50);
    var platform = String(req.query.platform || "kw");
    if (PLATFORMS.indexOf(platform) < 0) platform = "kw";
    try {
      var res = await platSearch(platform, keyword, page, limit);
      return res;
    } catch (e) { return err(reply, 502, e); }
  });
  app.get("/api/search/platforms", async () => {
    return { list: PLATFORMS.map(function (k) { return { id: k, name: PLATFORM_NAMES[k] }; }) };
  });
  // 全平台并发搜索：每平台独立成败，单点故障不拖垮整体
  app.get("/api/search/all", async (req, reply) => {
    var keyword = String(req.query.keyword || "").trim();
    if (!keyword) return err(reply, 400, "缺少 keyword");
    var page = Number(req.query.page) || 1;
    var limit = Math.min(Number(req.query.limit) || 30, 50);
    var plats = String(req.query.platforms || "").split(",").filter(function (x) { return PLATFORMS.indexOf(x) >= 0; });
    if (!plats.length) plats = PLATFORMS;
    var results = await Promise.all(plats.map(function (pl) {
      return new Promise(function (res) {
        platSearch(pl, keyword, page, limit).then(function (r) {
          res({ platform: pl, name: PLATFORM_NAMES[pl], ok: true, count: (r.list || []).length, list: r.list || [] });
        }).catch(function (e) {
          res({ platform: pl, name: PLATFORM_NAMES[pl], ok: false, error: String(e.message || e).slice(0, 120), count: 0, list: [] });
        });
      });
    }));
    return { keyword: keyword, page: page, results: results };
  });

  // ===== tasks =====
  app.get("/api/tasks", async (req) => {
    var where = "";
    var params = [];
    if (req.query.status) { where = "WHERE status=?"; params = [req.query.status]; }
    var rows = params.length
      ? getDb().prepare("SELECT * FROM tasks " + where + " ORDER BY created_at DESC LIMIT 300").all(...params)
      : getDb().prepare("SELECT * FROM tasks ORDER BY created_at DESC LIMIT 300").all();
    return { list: rows };
  });
  app.post("/api/tasks/download", async (req, reply) => {
    var body = req.body || {};
    var items = body.items || [];
    var quality = body.quality || String(cfg("default_quality", "auto"));
    if (["auto", "flac", "320k", "128k"].indexOf(String(quality)) < 0) quality = "auto";
    items = (Array.isArray(items) ? items : []).filter(function (x) { return x && (x.id || x.songmid); });
    if (!items.length) return err(reply, 400, "items 为空");
    if (!hasAnyBox()) return err(reply, 400, "没有可用的自定义音源，无法获取播放地址（请先在音源页上传并启用）");
    var ids = [];
    for (var it of items) {
      var sid = body.source_id || "";
      ids.push(enqueueDownload(it, quality, sid));
    }
    return { ok: true, ids: ids };
  });
  app.post("/api/tasks/:id/retry", async (req, reply) => {
    try { retryTask(req.params.id); return { ok: true }; } catch (e) { return err(reply, 400, e); }
  });
  app.post("/api/tasks/:id/cancel", async (req) => { cancelTask(req.params.id); return { ok: true }; });
  app.delete("/api/tasks/finished", async () => { clearFinished(); return { ok: true }; });

  // ===== tracks =====
  app.get("/api/tracks", async (req) => {
    var q = String(req.query.q || "");
    var rows;
    if (q) {
      rows = getDb().prepare("SELECT * FROM tracks WHERE title LIKE ? OR artist LIKE ? OR album LIKE ? ORDER BY created_at DESC LIMIT 500").all("%" + q + "%", "%" + q + "%", "%" + q + "%");
    } else {
      rows = getDb().prepare("SELECT * FROM tracks ORDER BY created_at DESC LIMIT 500").all();
    }
    return { list: rows };
  });
  app.delete("/api/tracks/:id", async (req, reply) => {
    var t = getDb().prepare("SELECT * FROM tracks WHERE id=?").get(req.params.id);
    if (!t) return err(reply, 404, "不存在");
    try { if (t.remote_path) await deleteRemote(t.remote_path); } catch (e) { logEvent("del_warn", t.remote_path + ": " + e.message); }
    try { if (t.remote_path) await deleteRemote(t.remote_path.replace(/\.[a-z0-9]+$/i, "") + ".lrc"); } catch (e) {}
    try { if (t.remote_path) await deleteRemote(t.remote_path.replace(/\.[a-z0-9]+$/i, "") + ".jpg"); } catch (e) {}
    getDb().prepare("DELETE FROM tracks WHERE id=?").run(req.params.id);
    try { refreshDir("music/_incoming"); } catch (e) {}
    logEvent("track_del", t.title);
    return { ok: true };
  });
  app.post("/api/tracks/:id/organize", async (req, reply) => {
    try { return await organizeTrack(req.params.id); } catch (e) { return err(reply, 500, e); }
  });
  app.post("/api/tracks/:id/scrape", async (req, reply) => {
    var t = getDb().prepare("SELECT * FROM tracks WHERE id=?").get(req.params.id);
    if (!t) return err(reply, 404, "不存在");
    var r = await mtScrapeTrack(t);
    if (!r.ok) return err(reply, 502, r.reason || JSON.stringify(r.detail || ("HTTP " + r.status)).slice(0, 200));
    return r;
  });
  // 预览：WebDAV 流式透传（支持 Range，浏览器 audio 直接播放）
  app.get("/api/preview/:id", async (req, reply) => {
    var t = getDb().prepare("SELECT * FROM tracks WHERE id=?").get(req.params.id);
    if (!t || !t.remote_path) return err(reply, 404, "文件不存在");
    if (!webdavReady()) return err(reply, 400, "WebDAV 未配置");
    var ext = String(t.remote_path).split(".").pop().toLowerCase();
    var mime = ext === "flac" ? "audio/flac" : (ext === "ogg" ? "audio/ogg" : (ext === "m4a" ? "audio/mp4" : "audio/mpeg"));
    var base = String(cfg("webdav_url", "")).replace(/\/+$/, "");
    var relPath = davPath(t.remote_path);
    var url = base.replace(/\/+$/, "").replace(/\/webdav$/i, "/webdav/") + "/" + relPath.replace(/^\/+/, "").split("/").map(function (s) { return encodeURIComponent(s); }).join("/");
    var auth = "Basic " + Buffer.from(String(cfg("webdav_user", "")) + ":" + String(cfg("webdav_pass", ""))).toString("base64");
    var headers = { Authorization: auth };
    if (req.headers.range) headers.Range = req.headers.range;
    var upstream = await fetch(url, { headers, redirect: "follow" });
    if (!upstream.ok && upstream.status !== 206) {
      return err(reply, 502, "网盘返回 " + upstream.status);
    }
    reply.code(upstream.status === 206 ? 206 : 200);
    reply.header("Content-Type", mime);
    reply.header("Accept-Ranges", "bytes");
    if (upstream.headers.get("content-length")) reply.header("Content-Length", upstream.headers.get("content-length"));
    if (upstream.headers.get("content-range")) reply.header("Content-Range", upstream.headers.get("content-range"));
    reply.header("Cache-Control", "no-store");
    return reply.send(Readable.fromWeb(upstream.body));
  });
  // Music Tag Web 连通与登录自测
  app.post("/api/musictag/test", async (req, reply) => {
    var probe = await mtProbe();
    if (!probe.ok) return err(reply, 502, "Music Tag Web 不可达: " + (probe.error || probe.status));
    try {
      var token = await mtLogin(true);
      return { ok: true, login: "ok", token_head: String(token).slice(0, 12) + "..." };
    } catch (e) { return err(reply, 502, "MTW 可达但登录失败: " + String(e.message || e)); }
  });

  // ===== organize =====
  app.get("/api/organize/incoming", async () => {
    if (!webdavReady()) return { list: [], note: "WebDAV 未配置" };
    var items = await listRemote("_incoming");
    return { list: items.filter(function (i) { return !i.directory; }).map(function (i) { return { name: i.basename, size: i.size || 0, path: i.filename }; }) };
  });
  app.post("/api/organize/run", async (req, reply) => {
    try {
      var r = await organizeAllPending();
      if (r.ok > 0) startScan().catch(function () {});
      return r;
    } catch (e) { return err(reply, 500, e); }
  });
  app.get("/api/organize/preview", async (req, reply) => {
    var t = getDb().prepare("SELECT * FROM tracks WHERE id=?").get(req.query.track || "");
    if (!t) return err(reply, 404, "track 不存在");
    return { target: await computeTargetPath(t, req.query.template) };
  });

  // ===== settings =====
  var SECRET_KEYS = ["webdav_pass", "console_password", "mt_pass", "nav_pass", "mt_token"];
  app.get("/api/settings", async () => {
    var all = allSettings();
    for (var k of SECRET_KEYS) if (all[k]) all[k] = "***";
    return { values: all, defaults: { naming_template: "{{artist}}/{{album}}/{{trackNumber}} - {{title}}", default_quality: "320k", dl_concurrency: 3, cache_target_gb: 10 } };
  });
  app.put("/api/settings", async (req, reply) => {
    try {
      var allowed = ["webdav_url", "webdav_user", "webdav_pass", "webdav_root", "default_quality", "dl_concurrency", "dl_speed_limit_kbps", "naming_template", "auto_organize", "auto_scrape", "nav_user", "nav_pass", "navidrome_url", "mt_user", "mt_pass", "musictag_url", "mt_sources", "mt_media_root", "mt_token", "cache_target_gb"];
      var body = req.body || {};
      for (var k of allowed) {
        if (!(k in body)) continue;
        var v = body[k];
        if (SECRET_KEYS.indexOf(k) >= 0 && (v === "***" || v === "" || v === null || v === undefined)) continue;
        setSetting(k, v);
      }
      logEvent("settings", Object.keys(body).join(","));
      return { ok: true };
    } catch (e) { return err(reply, 400, e); }
  });

  // ===== webdav test =====
  app.post("/api/webdav/test", async (req, reply) => {
    try {
      if (!webdavReady()) return err(reply, 400, "WebDAV 未配置完整");
      var r = await testConnection();
      // 测试后刷新 rclone 使远端目录可见
      try { await refreshDir("music", true); } catch (e) {}
      return { ok: true, detail: r };
    } catch (e) { return err(reply, 502, e); }
  });

  // ===== system =====
  app.get("/api/system/status", async () => {
    var out = { services: {}, disk: {}, mem: {}, counts: {} };
    out.services.musicpipe = "ok";
    try { out.services.rclone = await rcloneStatus(); } catch (e) { out.services.rclone = { ok: false, error: String(e.message || e) }; }
    out.services.webdav = { configured: webdavReady() };
    try { out.services.navidrome = await navPing(); } catch (e) { out.services.navidrome = { ok: false, error: String(e.message || e) }; }
    try { out.services.musictag = await mtProbe(); } catch (e) { out.services.musictag = { ok: false, error: String(e.message || e) }; }
    try {
      var s = fs.statfsSync ? fs.statfsSync(ENV.musicMount) : null;
      if (s) out.disk = { total: s.blocks * s.bsize, free: s.bfree * s.bsize };
    } catch (e) {}
    out.mem = { total: os.totalmem(), free: os.freemem() };
    try {
      out.counts = {
        tracks: getDb().prepare("SELECT COUNT(*) c FROM tracks").get().c,
        tracks_imported: getDb().prepare("SELECT COUNT(*) c FROM tracks WHERE status='imported'").get().c,
        tasks_running: getDb().prepare("SELECT COUNT(*) c FROM tasks WHERE status IN ('pending','downloading','verifying','tagging','uploading','organizing')").get().c,
        today_downloads: getDb().prepare("SELECT COUNT(*) c FROM tasks WHERE type='download' AND status='done' AND finished_at > ?").get(Date.now() - 86400000).c,
      };
    } catch (e) { out.counts = {}; }
    out.workers = poolSize();
    out.nav_configured = !!(cfg("nav_user", "") && cfg("nav_pass", ""));
    return out;
  });
  app.get("/api/system/scan-status", async (req, reply) => {
    try { return await scanStatus(); } catch (e) { return err(reply, 502, e); }
  });
  app.post("/api/system/scan", async (req, reply) => {
    try { return await startScan(); } catch (e) { return err(reply, 502, e); }
  });
  app.post("/api/system/refresh", async (req, reply) => {
    try { return await refreshDir("music", true); } catch (e) { return err(reply, 502, e); }
  });

  // ===== 分享链接订阅 =====
  app.post("/api/sharelinks", async (req, reply) => {
    try {
      var b = req.body || {};
      return await prepareShareLink(String(b.url || ""), b.quality ? String(b.quality) : "", b.share_user ? String(b.share_user) : "");
    } catch (e) { return err(reply, 400, e); }
  });
  app.get("/api/sharelinks/:id/preview", async (req, reply) => {
    try { return getSharePreview(req.params.id); }
    catch (e) { return err(reply, 404, e); }
  });
  app.post("/api/sharelinks/:id/commit", async (req, reply) => {
    try {
      var b = req.body || {};
      return await commitShareLink(req.params.id, { share_user: b.share_user ? String(b.share_user) : "", quality: b.quality ? String(b.quality) : "", keys: Array.isArray(b.keys) ? b.keys : null });
    } catch (e) { return err(reply, 400, e); }
  });
  // ===== 批量操作 =====
  app.post("/api/tracks/bulk-delete", async (req, reply) => {
    var ids = (req.body && req.body.ids) || [];
    if (!ids.length) return err(reply, 400, "ids 为空");
    var del = 0, errs = [];
    for (var id of ids) {
      try {
        var t = getDb().prepare("SELECT * FROM tracks WHERE id=?").get(id);
        if (!t) continue;
        try { if (t.remote_path) await deleteRemote(t.remote_path); } catch (e) { errs.push(t.title + ": " + String(e.message).slice(0, 40)); }
        try { if (t.remote_path) await deleteRemote(t.remote_path.replace(/\.[a-z0-9]+$/i, "") + ".lrc"); } catch (e) {}
        try { if (t.remote_path) await deleteRemote(t.remote_path.replace(/\.[a-z0-9]+$/i, "") + ".jpg"); } catch (e) {}
        getDb().prepare("DELETE FROM tracks WHERE id=?").run(id);
        del++;
      } catch (e) { errs.push(String(id) + ": " + String(e.message).slice(0, 40)); }
    }
    try { await refreshDir("music", true); } catch (e) {}
    logEvent("bulk_del", "tracks x" + del);
    return { ok: true, deleted: del, errors: errs.slice(0, 5) };
  });
  app.post("/api/tasks/bulk-cancel", async (req, reply) => {
    var ids = (req.body && req.body.ids) || [];
    if (!ids.length) return err(reply, 400, "ids 为空");
    var n = 0;
    for (var id of ids) { try { cancelTask(id); n++; } catch (e) { var r = getDb().prepare("UPDATE tasks SET status='cancelled', updated_at=? WHERE id=? AND status!='done'").run(Date.now(), id); if (r.changes) n++; } }
    return { ok: true, cancelled: n };
  });
  app.post("/api/tasks/bulk-delete", async (req, reply) => {
    var ids = (req.body && req.body.ids) || [];
    if (!ids.length) return err(reply, 400, "ids 为空");
    var st = getDb().prepare("SELECT status FROM tasks WHERE id=?");
    var del = 0;
    for (var id of ids) {
      var s0 = st.get(id);
      if (!s0) continue;
      if (!["done", "failed", "cancelled"].includes(s0.status)) continue; // 运行中不许直接删记录
      getDb().prepare("DELETE FROM tasks WHERE id=?").run(id);
      del++;
    }
    return { ok: true, deleted: del };
  });
  app.get("/api/sharelinks", async () => {
    var links = getDb().prepare("SELECT * FROM sharelinks ORDER BY added_at DESC").all();
    for (var l of links) {
      l.new_added = getDb().prepare("SELECT COUNT(*) c FROM sharelink_items WHERE link_id=? AND state='queued'").get(l.id).c;
      l.in_library = getDb().prepare("SELECT COUNT(*) c FROM sharelink_items WHERE link_id=? AND (state='done' OR state='exists')").get(l.id).c;
    }
    return { list: links };
  });
  app.post("/api/sharelinks/:id/sync", async (req, reply) => {
    try { return await syncShareLink(req.params.id, { force: !!(req.body && req.body.force) }); }
    catch (e) { return err(reply, 500, e); }
  });
  app.post("/api/sharelinks/:id/pause", async (req, reply) => {
    getDb().prepare("UPDATE sharelinks SET status=CASE WHEN status='paused' THEN 'active' ELSE 'paused' END WHERE id=?").run(req.params.id);
    return { ok: true };
  });
  app.delete("/api/sharelinks/:id", async (req) => {
    var db2 = getDb();
    try {
      var rows = db2.prepare("SELECT task_id FROM sharelink_items WHERE link_id=?").all(req.params.id);
      var pend = db2.prepare("UPDATE tasks SET status='cancelled', updated_at=? WHERE id=? AND status IN ('pending','queued')");
      for (var x of rows) if (x.task_id) pend.run(Date.now(), x.task_id);
    } catch (e) {}
    db2.prepare("DELETE FROM sharelink_items WHERE link_id=?").run(req.params.id);
    db2.prepare("DELETE FROM sharelinks WHERE id=?").run(req.params.id);
    return { ok: true };
  });
  app.get("/api/sharelinks/:id/songs", async (req) => {
    var rows = getDb().prepare("SELECT * FROM sharelink_items WHERE link_id=? ORDER BY no").all(req.params.id);
    return { list: rows };
  });

  // ===== events 最近日志 =====
  app.get("/api/events", async () => {
    var rows = getDb().prepare("SELECT * FROM events ORDER BY id DESC LIMIT 50").all();
    return { list: rows };
  });
}
