import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { getDb, logEvent } from "../db/index.js";
import { ENV } from "../config.js";
import { hashPassword, verifyPassword } from "../util.js";
import { createSession, destroySession, sessionUser, tokenOf } from "../auth.js";

var USERNAME_RE = /^[A-Za-z0-9_\u4e00-\u9fa5]{3,32}$/;
var APK_MAX = 300 * 1024 * 1024;

function apkDir() {
  var d = path.join(ENV.localDir, "apk");
  fs.mkdirSync(d, { recursive: true });
  return d;
}

function err(reply, code, msg) { reply.code(code); return { error: msg }; }

function userRow(u) {
  return { id: u.id, username: u.username, role: u.role, createdAt: u.created_at, created_at: u.created_at };
}

function releaseRow(r) {
  return {
    id: r.id,
    versionName: r.version_name,
    versionCode: r.version_code,
    md5: r.md5,
    size: r.size,
    changelog: r.changelog || "",
    downloadUrl: "/api/v1/app/releases/download?versionCode=" + r.version_code,
    createdAt: r.created_at,
  };
}

// 需要登录（authHook 已挡未登录，这里做用户解析）；返回会话用户或 null(已回 401)
function needUser(req, reply) {
  var u = sessionUser(req);
  if (!u) { reply.code(401); reply.send({ error: "unauthorized" }); return null; }
  return u;
}

// 需要管理员：App admin 角色或控制台属主会话
function needAdmin(req, reply) {
  var u = needUser(req, reply);
  if (!u) return null;
  if (u.console || u.role === "admin") return u;
  reply.code(403); reply.send({ error: "forbidden" });
  return null;
}

function countAdmins() {
  return getDb().prepare("SELECT COUNT(*) c FROM users WHERE role='admin'").get().c;
}

export function registerAppSvc(app) {
  // ===== App 账号（用户名+密码，多用户+角色）=====
  app.post("/api/v1/accounts/register", async (req, reply) => {
    var b = req.body || {};
    var username = String(b.username || "").trim();
    var password = String(b.password || "");
    if (!USERNAME_RE.test(username)) return err(reply, 400, "invalid_username");
    if (password.length < 6) return err(reply, 400, "invalid_password");
    var exists = getDb().prepare("SELECT id FROM users WHERE username=?").get(username);
    if (exists) return err(reply, 409, "username_exists");
    var role = countAdmins() === 0 && getDb().prepare("SELECT COUNT(*) c FROM users").get().c === 0 ? "admin" : "user";
    var now = Date.now();
    var r = getDb().prepare("INSERT INTO users(username,password_hash,role,created_at) VALUES(?,?,?,?)").run(username, hashPassword(password), role, now);
    logEvent("user_register", username + " (" + role + ")");
    reply.code(201);
    return { id: Number(r.lastInsertRowid), username: username, role: role, createdAt: now };
  });

  app.post("/api/v1/accounts/login", async (req, reply) => {
    var b = req.body || {};
    var username = String(b.username || "").trim();
    var password = String(b.password || "");
    var u = getDb().prepare("SELECT * FROM users WHERE username=?").get(username);
    if (!u) return err(reply, 404, "user_not_found");
    if (!verifyPassword(password, u.password_hash)) return err(reply, 401, "wrong_password");
    var token = createSession(u.id);
    logEvent("user_login", username);
    return { ok: true, token: token, expiresIn: 2592000, id: u.id, username: u.username, role: u.role, user: { id: u.id, username: u.username, role: u.role, createdAt: u.created_at } };
  });

  app.get("/api/v1/accounts/me", async (req, reply) => {
    var u = needUser(req, reply); if (!u) return reply;
    if (u.console) return { id: 0, username: "console", role: "admin", createdAt: 0 };
    return { id: u.id, username: u.username, role: u.role, createdAt: u.createdAt };
  });

  app.post("/api/v1/accounts/logout", async (req) => {
    var t = tokenOf(req);
    if (t) destroySession(t);
    return { ok: true };
  });

  // ===== 用户管理（admin/控制台）=====
  app.get("/api/v1/users", async (req, reply) => {
    var u = needAdmin(req, reply); if (!u) return reply;
    var page = Math.max(Number(req.query.page) || 1, 1);
    var limit = Math.min(Math.max(Number(req.query.limit) || 20, 1), 100);
    var total = getDb().prepare("SELECT COUNT(*) c FROM users").get().c;
    var rows = getDb().prepare("SELECT * FROM users ORDER BY id LIMIT ? OFFSET ?").all(limit, (page - 1) * limit);
    return { items: rows.map(userRow), total: total, page: page, limit: limit };
  });

  app.post("/api/v1/users", async (req, reply) => {
    var u = needAdmin(req, reply); if (!u) return reply;
    var b = req.body || {};
    var username = String(b.username || "").trim();
    var password = String(b.password || "");
    var role = b.role === "admin" ? "admin" : "user";
    if (!USERNAME_RE.test(username)) return err(reply, 400, "invalid_username");
    if (password.length < 6) return err(reply, 400, "invalid_password");
    if (getDb().prepare("SELECT id FROM users WHERE username=?").get(username)) return err(reply, 409, "username_exists");
    var now = Date.now();
    var r = getDb().prepare("INSERT INTO users(username,password_hash,role,created_at) VALUES(?,?,?,?)").run(username, hashPassword(password), role, now);
    logEvent("user_create", username + " (" + role + ")");
    reply.code(201);
    return { id: Number(r.lastInsertRowid), username: username, role: role, createdAt: now };
  });

  app.put("/api/v1/users/:id", async (req, reply) => {
    var u = needAdmin(req, reply); if (!u) return reply;
    var id = Number(req.params.id);
    var row = getDb().prepare("SELECT * FROM users WHERE id=?").get(id);
    if (!row) return err(reply, 404, "user_not_found");
    var b = req.body || {};
    if (b.password !== undefined && b.password !== null && String(b.password) !== "") {
      if (String(b.password).length < 6) return err(reply, 400, "invalid_password");
      getDb().prepare("UPDATE users SET password_hash=? WHERE id=?").run(hashPassword(String(b.password)), id);
    }
    if (b.role === "admin" || b.role === "user") {
      if (b.role !== row.role) {
        if (!u.console && row.role === "admin" && b.role === "user" && countAdmins() <= 1) return err(reply, 400, "last_admin"); // 控制台属主可越过
        if (!u.console && u.id === id && b.role !== "admin") return err(reply, 400, "self_demote");
        getDb().prepare("UPDATE users SET role=? WHERE id=?").run(b.role, id);
      }
    }
    logEvent("user_update", row.username);
    return { ok: true };
  });

  app.delete("/api/v1/users/:id", async (req, reply) => {
    var u = needAdmin(req, reply); if (!u) return reply;
    var id = Number(req.params.id);
    var row = getDb().prepare("SELECT * FROM users WHERE id=?").get(id);
    if (!row) return err(reply, 404, "user_not_found");
    if (!u.console && u.id === id) return err(reply, 400, "self_delete");
    if (!u.console && row.role === "admin" && countAdmins() <= 1) return err(reply, 400, "last_admin"); // 控制台属主可越过
    getDb().prepare("DELETE FROM sessions WHERE user_id=?").run(String(id));
    getDb().prepare("DELETE FROM users WHERE id=?").run(id);
    logEvent("user_delete", row.username);
    return { ok: true };
  });

  // ===== APK 发布 / App 更新服务 =====
  app.get("/api/v1/app/releases", async (req, reply) => {
    var u = needAdmin(req, reply); if (!u) return reply;
    var rows = getDb().prepare("SELECT * FROM app_releases ORDER BY version_code DESC").all();
    return { items: rows.map(releaseRow) };
  });

  app.post("/api/v1/app/releases", async (req, reply) => {
    var u = needAdmin(req, reply); if (!u) return reply;
    var fields = {};
    var fileBuf = null, fileName = "";
    try {
      for await (var part of req.parts()) {
        if (part.type === "file") {
          fileName = String(part.filename || "");
          fileBuf = await part.toBuffer();
        } else {
          fields[String(part.fieldname)] = part.value;
        }
      }
    } catch (e) { return err(reply, 400, "multipart_parse_failed: " + String(e.message || e).slice(0, 120)); }
    var versionName = String(fields.versionName || "").trim();
    var versionCode = Number(String(fields.versionCode || "").trim());
    var changelog = String(fields.changelog || "");
    if (!versionName || versionName.length > 32) return err(reply, 400, "invalid_version_name");
    if (!Number.isInteger(versionCode) || versionCode <= 0) return err(reply, 400, "invalid_version_code");
    if (!fileBuf || !fileBuf.length) return err(reply, 400, "file_required");
    if (!/\.apk$/i.test(fileName)) return err(reply, 400, "apk_only");
    if (fileBuf.length > APK_MAX) return err(reply, 400, "file_too_large");
    if (getDb().prepare("SELECT id FROM app_releases WHERE version_code=?").get(versionCode)) return err(reply, 409, "duplicate_version_code");
    var md5 = crypto.createHash("md5").update(fileBuf).digest("hex");
    var now = Date.now();
    var file = path.join(apkDir(), versionCode + "-" + now + ".apk");
    fs.writeFileSync(file, fileBuf);
    var r = getDb().prepare("INSERT INTO app_releases(version_name,version_code,md5,size,changelog,file_path,created_at) VALUES(?,?,?,?,?,?,?)")
      .run(versionName, versionCode, md5, fileBuf.length, changelog, file, now);
    logEvent("release_upload", versionName + " (vc=" + versionCode + ", " + fileBuf.length + "B)");
    reply.code(201);
    return releaseRow(getDb().prepare("SELECT * FROM app_releases WHERE id=?").get(Number(r.lastInsertRowid)));
  });

  app.get("/api/v1/app/update/latest", async (req, reply) => {
    var u = needUser(req, reply); if (!u) return reply;
    var row = getDb().prepare("SELECT * FROM app_releases ORDER BY version_code DESC LIMIT 1").get();
    if (!row) return err(reply, 404, "no_release");
    return releaseRow(row);
  });

  app.get("/api/v1/app/releases/download", async (req, reply) => {
    var u = needUser(req, reply); if (!u) return reply;
    var row = null;
    if (req.query.versionCode) row = getDb().prepare("SELECT * FROM app_releases WHERE version_code=?").get(Number(req.query.versionCode));
    else if (req.query.id) row = getDb().prepare("SELECT * FROM app_releases WHERE id=?").get(Number(req.query.id));
    if (!row) return err(reply, 404, "release_not_found");
    if (!fs.existsSync(row.file_path)) return err(reply, 410, "file_missing");
    var st = fs.statSync(row.file_path);
    reply.header("Content-Type", "application/vnd.android.package-archive");
    reply.header("Content-Length", st.size);
    reply.header("Content-Disposition", 'attachment; filename="musicpipe-v' + row.version_name + '.apk"');
    reply.header("Cache-Control", "no-store");
    return reply.send(fs.createReadStream(row.file_path));
  });

  app.delete("/api/v1/app/releases/:id", async (req, reply) => {
    var u = needAdmin(req, reply); if (!u) return reply;
    var row = getDb().prepare("SELECT * FROM app_releases WHERE id=?").get(Number(req.params.id));
    if (!row) return err(reply, 404, "release_not_found");
    try { if (row.file_path && fs.existsSync(row.file_path)) fs.unlinkSync(row.file_path); } catch (e) {}
    getDb().prepare("DELETE FROM app_releases WHERE id=?").run(row.id);
    logEvent("release_delete", row.version_name + " (vc=" + row.version_code + ")");
    return { ok: true };
  });
}
