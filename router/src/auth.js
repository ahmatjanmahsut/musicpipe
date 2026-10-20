import crypto from "node:crypto";
import { getDb } from "./db/index.js";

const SESSION_MS = 1000 * 60 * 60 * 24 * 30;

export function createSession(userId) {
  var token = crypto.randomBytes(24).toString("hex");
  var now = Date.now();
  getDb().prepare("INSERT INTO sessions(token,created_at,expires_at,user_id) VALUES(?,?,?,?)").run(token, now, now + SESSION_MS, userId === undefined || userId === null ? "" : String(userId));
  return token;
}

// 从请求中提取会话 token：Bearer 头 > mp_session cookie > ?token= 查询参数
export function tokenOf(req) {
  var h = req.headers && req.headers.authorization;
  if (h && /^Bearer\s+/i.test(String(h))) return String(h).replace(/^Bearer\s+/i, "").trim();
  if (req.cookies && req.cookies.mp_session) return String(req.cookies.mp_session);
  try { if (req.query && req.query.token) return String(req.query.token); } catch (e) {}
  return null;
}

// 解析会话所属用户。历史控制台会话(user_id='')视为属主(admin 权能，v5 迁移后已不再产生)；其余返回 users 行。
export function sessionUser(req) {
  var token = tokenOf(req);
  if (!token || !isValidSession(token)) return null;
  var s = null;
  try { s = getDb().prepare("SELECT user_id FROM sessions WHERE token=?").get(token); } catch (e) {}
  if (!s) return null;
  if (!s.user_id) return { console: true, role: "admin", username: "console" };
  var u = getDb().prepare("SELECT id,username,role,created_at FROM users WHERE id=?").get(Number(s.user_id));
  if (!u) return null;
  return { console: false, id: u.id, username: u.username, role: u.role, createdAt: u.created_at };
}

export function isValidSession(token) {
  if (!token) return false;
  var r = getDb().prepare("SELECT expires_at FROM sessions WHERE token=?").get(token);
  if (!r) return false;
  if (r.expires_at < Date.now()) {
    getDb().prepare("DELETE FROM sessions WHERE token=?").run(token);
    return false;
  }
  return true;
}

export function destroySession(token) {
  if (token) getDb().prepare("DELETE FROM sessions WHERE token=?").run(token);
}

// v5 账号体系统一：users 表是唯一账号来源（控制台与 App 共用）
export function hasAnyUser() {
  try { return getDb().prepare("SELECT COUNT(*) c FROM users").get().c > 0; } catch (e) { return false; }
}

// 统一鉴权闸门：
// - /api/auth/*        放行（登录/初始化/状态自身）
// - /api/v1/accounts/register|login 放行（App 注册/登录）
// - /api/v1/*          任意有效会话（admin 与 user 均可，App 网关全功能）
// - /api/*（控制台域） 仅管理员会话（403 forbidden 区别于 401 unauthorized）
export async function authHook(req, reply) {
  if (req.url.startsWith("/api/auth/")) return;
  if (req.url.startsWith("/api/v1/accounts/register")) return;
  if (req.url.startsWith("/api/v1/accounts/login")) return;
  if (!req.url.startsWith("/api/")) return; // 静态资源放行
  var token = tokenOf(req);
  if (!token || !isValidSession(token)) {
    reply.code(401); return reply.send({ error: "unauthorized" });
  }
  if (!req.url.startsWith("/api/v1/")) {
    var u = sessionUser(req);
    if (!u || (u.role !== "admin" && !u.console)) {
      reply.code(403); return reply.send({ error: "forbidden" });
    }
  }
}
