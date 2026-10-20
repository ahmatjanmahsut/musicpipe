import crypto from "node:crypto";

export function newId() {
  return Date.now().toString(36) + crypto.randomBytes(6).toString("hex");
}

export function hashPassword(pw) {
  const salt = crypto.randomBytes(16).toString("hex");
  const h = crypto.scryptSync(pw, salt, 64).toString("hex");
  return salt + ":" + h;
}

export function verifyPassword(pw, stored) {
  if (!stored || String(stored).indexOf(":") < 0) return false;
  const parts = String(stored).split(":");
  const t = crypto.scryptSync(pw, parts[0], 64).toString("hex");
  try {
    return crypto.timingSafeEqual(Buffer.from(parts[1], "hex"), Buffer.from(t, "hex"));
  } catch { return false; }
}

export function subsonicParams(user, pass) {
  const salt = crypto.randomBytes(12).toString("hex");
  const token = crypto.createHash("md5").update(pass + salt).digest("hex");
  return "u=" + encodeURIComponent(user) + "&t=" + token + "&s=" + salt + "&v=1.16.1&c=musicpipe&f=json";
}

export function sanitizeName(s, max = 120) {
  if (!s) return "";
  var out = String(s).replace(/[\/:*?"<>|]/g, "_").replace(/\s+/g, " ").trim();
  out = out.replace(/\.\.{2,}/g, ".");
  return out.slice(0, max);
}

export function formatBytes(n) {
  if (!Number.isFinite(n)) return "-";
  var u = ["B", "KB", "MB", "GB", "TB"], i = 0;
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
  return n.toFixed(i ? 1 : 0) + " " + u[i];
}

export class RateLimiter {
  constructor(kbps) { this.rate = kbps > 0 ? kbps * 1024 : 0; this.acc = 0; this.last = Date.now(); }
  async wait(bytes) {
    if (!this.rate) return;
    var now = Date.now();
    this.acc += ((now - this.last) / 1000) * this.rate;
    this.last = now;
    if (this.acc < 0) this.acc = 0;
    if (this.acc >= bytes) { this.acc -= bytes; return; }
    var deficit = bytes - this.acc;
    this.acc = 0;
    await new Promise(function (r) { setTimeout(r, (deficit / this.rate) * 1000); }.bind(this));
  }
}

export function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

export async function fetchWithTimeout(url, opts = {}, ms = 15000) {
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), ms);
  try {
    const r = await fetch(url, Object.assign({}, opts, { signal: opts.signal || ctl.signal, redirect: "follow" }));
    return r;
  } finally { clearTimeout(timer); }
}
