import fs from "node:fs";
import path from "node:path";
import { getDb } from "../db/index.js";
import { newId } from "../util.js";
import { loadSourceScript } from "./loader.js";

var sourcesDir = null;
var boxes = new Map();

export function initRegistry(dir) {
  sourcesDir = dir;
  fs.mkdirSync(dir, { recursive: true });
  var rows = getDb().prepare("SELECT * FROM sources WHERE enabled=1").all();
  return Promise.all(rows.map(function (row) { return tryLoad(row); }));
}

async function tryLoad(row) {
  try {
    var code = fs.readFileSync(path.join(sourcesDir, row.file), "utf8");
    var box = loadSourceScript(code);
    await box.waitInited();
    boxes.set(row.id, box);
    var platforms = box.platforms();
    var name = box.scriptInfo.name || row.name;
    getDb().prepare("UPDATE sources SET loaded_ok=1, last_error='', platforms=?, name=? WHERE id=?")
      .run(JSON.stringify(platforms), name, row.id);
    return { ok: true, id: row.id, platforms: platforms, name: name };
  } catch (e) {
    getDb().prepare("UPDATE sources SET loaded_ok=0, last_error=? WHERE id=?").run(String(e.message || e).slice(0, 500), row.id);
    return { ok: false, error: String(e.message || e) };
  }
}

export async function addSource(name, code) {
  var id = newId();
  var file = id + ".js";
  fs.writeFileSync(path.join(sourcesDir, file), code, "utf8");
  var row = { id: id, name: name || "未命名源", file: file };
  getDb().prepare("INSERT INTO sources(id,name,file,enabled,created_at) VALUES(?,?,?,1,?)").run(id, row.name, file, Date.now());
  var res = await tryLoad(row);
  if (!res.ok) {
    try { fs.unlinkSync(path.join(sourcesDir, file)); } catch (e) {}
    getDb().prepare("DELETE FROM sources WHERE id=?").run(id);
    throw new Error("源加载失败: " + res.error);
  }
  return { id: id, name: res.name, platforms: res.platforms };
}

export function removeSource(id) {
  var row = getDb().prepare("SELECT * FROM sources WHERE id=?").get(id);
  if (!row) throw new Error("源不存在");
  try { fs.unlinkSync(path.join(sourcesDir, row.file)); } catch (e) {}
  getDb().prepare("DELETE FROM sources WHERE id=?").run(id);
  boxes.delete(id);
}

export async function setEnabled(id, enabled) {
  getDb().prepare("UPDATE sources SET enabled=? WHERE id=?").run(enabled ? 1 : 0, id);
  if (enabled) {
    var row = getDb().prepare("SELECT * FROM sources WHERE id=?").get(id);
    return tryLoad(row);
  }
  boxes.delete(id);
  return { ok: true };
}

export function listSources() {
  return getDb().prepare("SELECT * FROM sources ORDER BY created_at DESC").all().map(function (r) {
    var platforms = [];
    try { platforms = JSON.parse(r.platforms); } catch (e) {}
    return Object.assign({}, r, { platforms: platforms, enabled: !!r.enabled, loaded_ok: !!r.loaded_ok });
  });
}

export function getBox(id) { return boxes.get(id); }
export function boxesList() { return Array.from(boxes.values()); }
export function activeBoxes() { return Array.from(boxes.entries()); }

// 找支持该平台的 box
export function boxForPlatform(platform, preferredId) {
  if (preferredId) {
    var b = boxes.get(preferredId);
    if (b && b.supports(platform)) return b;
  }
  for (var pair of boxes.entries()) {
    if (pair[1].supports(platform)) return pair[1];
  }
  return null;
}

export function hasAnyBox() { return boxes.size > 0; }

export function boxesForPlatform(platform, preferredId) {
  var arr = [];
  if (preferredId) {
    var b = boxes.get(preferredId);
    if (b && b.supports(platform)) arr.push({ id: preferredId, box: b });
  }
  for (var pair of boxes.entries()) {
    if (pair[0] === preferredId) continue;
    if (pair[1].supports(platform)) arr.push({ id: pair[0], box: pair[1] });
  }
  return arr;
}
