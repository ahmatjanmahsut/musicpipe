import { ENV, cfg } from "../config.js";
import { fetchWithTimeout } from "../util.js";

function base() { return String(cfg("navidrome_url", ENV.navidromeUrl)).replace(/\/+$/, ""); }

var jwtCache = { token: "", ts: 0 };

export async function navPing() {
  try {
    var r = await fetchWithTimeout(base() + "/", {}, 8000);
    return { ok: r.status < 500, status: r.status };
  } catch (e) { return { ok: false, error: String(e.message || e) }; }
}

export async function getJwt(force) {
  var now = Date.now();
  if (!force && jwtCache.token && now - jwtCache.ts < 20000000) return jwtCache.token;
  var user = String(cfg("nav_user", "")), pass = String(cfg("nav_pass", ""));
  if (!user || !pass) throw new Error("未配置 Navidrome 管理员账号（设置页）");
  var r = await fetchWithTimeout(base() + "/auth/login", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: user, password: pass })
  }, 10000);
  if (!r.ok) throw new Error("Navidrome 登录失败 HTTP " + r.status);
  var j = await r.json();
  if (!j || !j.token) throw new Error("Navidrome 登录未返回 token");
  jwtCache = { token: j.token, ts: now };
  return j.token;
}

export async function navRequest(method, extraParams) {
  var jwt = await getJwt();
  var qs = "u=" + encodeURIComponent(String(cfg("nav_user", ""))) + "&v=1.16.1&c=musicpipe&f=json&jwt=" + jwt + (extraParams ? "&" + extraParams : "");
  var r = await fetchWithTimeout(base() + "/rest/" + method + ".view?" + qs, {}, 20000);
  var text = await r.text();
  var resp;
  try { resp = JSON.parse(text)["subsonic-response"]; } catch (e) { throw new Error(method + " 非JSON响应: " + text.slice(0, 120)); }
  if (!resp || resp.status !== "ok") {
    jwt = await getJwt(true);
    qs = "u=" + encodeURIComponent(String(cfg("nav_user", ""))) + "&v=1.16.1&c=musicpipe&f=json&jwt=" + jwt + (extraParams ? "&" + extraParams : "");
    r = await fetchWithTimeout(base() + "/rest/" + method + ".view?" + qs, {}, 20000);
    resp = (await r.json())["subsonic-response"];
    if (!resp || resp.status !== "ok") throw new Error(method + " 失败: " + JSON.stringify(resp && resp.error));
  }
  return resp;
}

export async function startScan() {
  try {
    await navRequest("startScan");
    return { ok: true };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
}

export async function scanStatus() {
  try {
    var resp = await navRequest("getScanStatus");
    var sc = resp.scanStatus || {};
    return { ok: true, scanning: !!sc.scanning, count: sc.count || 0 };
  } catch (e) { return { ok: false, error: String(e.message || e) }; }
}