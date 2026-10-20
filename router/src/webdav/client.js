import { createClient } from "webdav";
import fs from "node:fs";
import { cfg } from "../config.js";

var _client = null;
var _signature = "";

function signature() {
  return [cfg("webdav_url", ""), cfg("webdav_user", ""), cfg("webdav_pass", ""), cfg("webdav_root", "/music")].join("|");
}

export function webdavReady() {
  return !!cfg("webdav_url", "") && !!cfg("webdav_user", "") && !!cfg("webdav_pass", "");
}

export function getClient(force) {
  var sig = signature();
  if (!_client || sig !== _signature || force) {
    if (!webdavReady()) throw new Error("WebDAV 未配置");
    _client = createClient(String(cfg("webdav_url", "")).replace(/\/+$/, ""), {
      username: String(cfg("webdav_user", "")),
      password: String(cfg("webdav_pass", "")),
      maxExecutionTime: 120000,
    });
    _signature = sig;
  }
  return _client;
}

export function davRoot() { return String(cfg("webdav_root", "/music")); }

export function davPath(rel) {
  var r = "/" + String(davRoot()).replace(/^\/+|\/+$/g, "");
  var p = String(rel).replace(/^\/+/, "");
  return p ? r + "/" + p : r;
}

export async function statRemote(rel) {
  try {
    var s = await getClient().stat(davPath(rel), { details: true });
    if (s && s.data && !s.filename) s = s.data;
    return s;
  } catch (e) { return null; }
}

export async function ensureDir(rel) {
  try { await getClient().createDirectory(davPath(rel)); } catch (e) {
    var msg = String((e && (e.message || e)) || "");
    if (!/exist|405|301|412/i.test(msg)) throw e;
  }
}

export async function ensureDirsFor(relFile) {
  var parts = relFile.split("/").slice(0, -1);
  var acc = "";
  for (var p of parts) {
    acc = acc ? acc + "/" + p : p;
    await ensureDir(acc);
  }
}

export async function putStream(relFile, localPath, onProgress) {
  var size = fs.statSync(localPath).size;
  var stream = fs.createReadStream(localPath);
  var loaded = 0;
  var { Transform } = await import("node:stream");
  var counter = new Transform({
    transform: function (chunk, enc, cb) { loaded += chunk.length; if (onProgress) onProgress(loaded, size); cb(null, chunk); }
  });
  await ensureDirsFor(relFile);
  await getClient().putFileContents(davPath(relFile), stream.pipe(counter), { overwrite: true });
  return size;
}

export async function moveRemote(fromRel, toRel) {
  await ensureDirsFor(toRel);
  var cl = getClient();
  try {
    await cl.moveFile(davPath(fromRel), davPath(toRel), true);
    return "move";
  } catch (e) {
    try {
      await cl.copyFile(davPath(fromRel), davPath(toRel), true);
      try { await cl.deleteFile(davPath(fromRel)); } catch (e2) {}
      return "copy";
    } catch (e2) {
      // 网盘不支持 MOVE/COPY（如123网盘）：下载重传
      var buf = await cl.getFileContents(davPath(fromRel));
      await cl.putFileContents(davPath(toRel), buf, { overwrite: true });
      try { await cl.deleteFile(davPath(fromRel)); } catch (e3) {}
      return "reupload";
    }
  }
}

export async function deleteRemote(rel) {
  await getClient().deleteFile(davPath(rel));
}

export async function listRemote(relDir) {
  try {
    var r = await getClient().getDirectoryContents(davPath(relDir), { traverseAll: false, details: true });
    if (Array.isArray(r)) return r;
    if (r && Array.isArray(r.data)) return r.data;
    return [];
  } catch (e) { return []; }
}

export async function testConnection() {
  var root = davRoot();
  var probe = ".mp_probe_" + Date.now() + ".txt";
  var ok = { url: cfg("webdav_url", ""), root: root };
  var files = await listRemote("");
  ok.listed = files.length >= 0 ? true : false;
  await getClient().putFileContents(davPath(probe), "probe", { overwrite: true });
  var s = await statRemote(probe);
  ok.write = !!(s && (s.size === 5));
  await getClient().deleteFile(davPath(probe));
  try {
    var s2 = await statRemote(probe);
    ok.delete = !s2;
  } catch (e) { ok.delete = true; }
  try { await getClient().moveFile(davPath(".mp_t1"), davPath(".mp_t2"), true); ok.move = true; }
  catch (e) {
    try { await getClient().putFileContents(davPath(".mp_t1"), "x", { overwrite: true }); await getClient().copyFile(davPath(".mp_t1"), davPath(".mp_t2"), true); ok.move = "copy-fallback"; await getClient().deleteFile(davPath(".mp_t1")); await getClient().deleteFile(davPath(".mp_t2")); }
    catch (e2) { ok.move = false; }
  }
  return ok;
}

export async function putBuffer(relFile, content) {
  await ensureDirsFor(relFile);
  return getClient().putFileContents(davPath(relFile), content, { overwrite: true });
}
