import { ENV } from "../config.js";
import { fetchWithTimeout } from "../util.js";

var RC = ENV.rcloneRc;

async function rc(call, params) {
  try {
    var r = await fetchWithTimeout(RC + "/" + call, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(params || {})
    }, 10000);
    if (!r.ok) return { error: "rc " + call + " http " + r.status };
    return await r.json();
  } catch (e) {
    return { error: String(e.message || e) };
  }
}

export function rclonePing() { return rc("core/version", {}); }
export function rcloneVfsStats() { return rc("vfs/stats", {}); }
export function rcloneCacheSize() { return rc("vfs/cache/size", {}); }

export async function refreshDir(dirRel, recursive) {
  var r = await rc("vfs/refresh", { dir: String(dirRel || "/").replace(/^\/+/, ""), recursive: recursive === true ? "true" : undefined });
  if (r && r.error) console.warn("[rclone] refresh failed:", r.error);
  return r;
}

export async function rcloneStatus() {
  var ping = await rclonePing();
  if (ping && ping.error) return { ok: false, error: ping.error };
  var cache = await rcloneCacheSize();
  var stats = await rcloneVfsStats();
  return { ok: !ping.error, cache_bytes: (cache && cache.size) || 0, stats: stats || null };
}
