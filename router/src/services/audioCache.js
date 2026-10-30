import fs from "node:fs";
import fsp from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import { pipeline } from "node:stream/promises";
import { Readable } from "node:stream";
import { ENV, cfg } from "../config.js";

const dir = path.join(ENV.localDir, "audio-cache");
const inflight = new Map();
const MAX_BYTES = 20 * 1024 * 1024 * 1024;
function fileOf(track) {
  const ext = path.extname(String(track.remote_path || "")).replace(/[^a-z0-9.]/gi, "") || ".audio";
  const key = crypto.createHash("sha256").update(String(track.id) + "|" + String(track.remote_path) + "|" + String(track.file_size || "")).digest("hex").slice(0, 24);
  return path.join(dir, key + ext);
}
async function exists(file) { try { const s = await fsp.stat(file); return s.isFile() && s.size > 0; } catch { return false; } }
async function touch(file) { const now = new Date(); try { await fsp.utimes(file, now, now); } catch {} }
export async function cachedAudio(track) {
  const file = fileOf(track);
  if (!(await exists(file))) return null;
  await touch(file);
  return file;
}
export async function audioCacheStats() {
  let count = 0, bytes = 0;
  try {
    for (const name of await fsp.readdir(dir)) {
      if (name.endsWith(".part")) continue;
      const s = await fsp.stat(path.join(dir, name));
      if (s.isFile()) { count++; bytes += s.size; }
    }
  } catch {}
  return { count, bytes, limitBytes: MAX_BYTES };
}
async function prune() {
  const files = [];
  for (const name of await fsp.readdir(dir).catch(() => [])) {
    if (name.endsWith(".part")) continue;
    const file = path.join(dir, name);
    const s = await fsp.stat(file).catch(() => null);
    if (s?.isFile()) files.push({ file, size: s.size, atime: s.atimeMs });
  }
  let total = files.reduce((n, f) => n + f.size, 0);
  files.sort((a, b) => a.atime - b.atime);
  for (const f of files) {
    if (total <= MAX_BYTES) break;
    await fsp.unlink(f.file).catch(() => {});
    total -= f.size;
  }
}
export function prefetchAudio(track) {
  if (inflight.has(track.id)) return inflight.get(track.id);
  const task = (async () => {
    const file = fileOf(track);
    if (await exists(file)) return file;
    await fsp.mkdir(dir, { recursive: true });
    const part = file + ".part";
    const base = String(cfg("webdav_url", "")).replace(/\/+$/, "").replace(/\/webdav$/i, "/webdav/");
    const { davPath } = await import("../webdav/client.js");
    const parts = davPath(track.remote_path).split("/").map(s => encodeURIComponent(s)).join("/");
    const auth = "Basic " + Buffer.from(String(cfg("webdav_user", "")) + ":" + String(cfg("webdav_pass", ""))).toString("base64");
    try {
      const up = await fetch(base + "/" + parts.replace(/^\/+/, ""), { headers: { Authorization: auth }, redirect: "follow" });
      if (!up.ok || !up.body) throw new Error("cache upstream " + up.status);
      await pipeline(Readable.fromWeb(up.body), fs.createWriteStream(part));
      await fsp.rename(part, file);
      await prune();
      return file;
    } catch (e) {
      await fsp.unlink(part).catch(() => {});
      console.warn("audio cache:", track.id, String(e.message || e));
      return null;
    } finally { inflight.delete(track.id); }
  })();
  inflight.set(track.id, task);
  return task;
}
