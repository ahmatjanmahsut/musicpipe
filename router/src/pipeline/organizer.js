import { getDb, logEvent } from "../db/index.js";
import { cfg } from "../config.js";
import { sanitizeName } from "../util.js";
import { moveRemote, statRemote, listRemote } from "../webdav/client.js";
import { refreshDir } from "../webdav/rclone.js";
import { mtScrapeTrack } from "./musictag.js";
import { sleep } from "../util.js";

export function renderTemplate(template, meta) {
  var tp = String(meta.trackNumber || "");
  var out = String(template)
    .replace(/\{\{artist\}\}/g, meta.artist || "未知歌手")
    .replace(/\{\{albumArtist\}\}/g, meta.albumArtist || meta.artist || "未知歌手")
    .replace(/\{\{album\}\}/g, meta.album || "单曲")
    .replace(/\{\{title\}\}/g, meta.title || "未知标题")
    .replace(/\{\{trackNumber\}\}/g, tp ? (Number(tp) < 10 ? "0" + tp : String(tp)) : "")
    .replace(/\{\{year\}\}/g, meta.year || "")
    .replace(/\{\{quality\}\}/g, meta.quality || "");
  // 清理空目录段与悬空 " - "
  out = out.split("/").map(function (s) { return s.replace(/^[\s.-]+|[\s.-]+$/g, "").trim(); }).filter(function (s) { return s; }).join("/");
  return out;
}

function uniqueSuffix() { return " [" + Date.now().toString(36).slice(-5) + "]"; }

export async function computeTargetPath(track, template) {
  var db = getDb();
  var meta = {
    artist: track.artist, album: track.album, title: track.title,
    trackNumber: "", quality: track.quality, year: ""
  };
  try {
    var extra = JSON.parse(track.extra || "{}");
    if (extra.meta) Object.assign(meta, { year: extra.meta.year });
  } catch (e) {}
  var shareCtx = null;
  try { var ex2 = JSON.parse(track.extra || "{}"); if (ex2.share && ex2.share.folder) shareCtx = ex2.share; } catch (e) {}
  var rel;
  if (shareCtx) {
    var no = Number(shareCtx.no || 0);
    var seq = no ? (no < 10 ? "0" + no : String(no)) : "";
    rel = shareCtx.folder + "/" + [seq, meta.title, meta.artist].filter(function (x) { return x; }).join(" - ");
  } else {
    rel = renderTemplate(template || String(cfg("naming_template", "{{artist}}/{{album}}/{{trackNumber}} - {{title}}")), meta);
  }
  var fname = rel.split("/").pop();
  var ext = (track.remote_path.split(".").pop() || "mp3");
  // 去重：目标已存在则加后缀
  var dir = rel.split("/").slice(0, -1).join("/");
  var items = await listRemote(dir);
  var cand = rel + "." + ext;
  var exists = new Set(items.map(function (i) { return i.basename; }));
  if (exists.has(fname)) cand = rel + uniqueSuffix() + "." + ext;
  return cand;
}

export async function organizeTrack(trackId) {
  var db = getDb();
  var track = db.prepare("SELECT * FROM tracks WHERE id=?").get(trackId);
  if (!track) throw new Error("track 不存在");
  if (track.status === "imported") return track;
  var target = await computeTargetPath(track);
  await moveRemote(track.remote_path, target);
  // 同名 .lrc 一起移动（存在才移）
  try {
    var lrcSrc = track.remote_path.replace(/\.[a-z0-9]+$/i, "") + ".lrc";
    var st = await statRemote(lrcSrc);
    if (st) await moveRemote(lrcSrc, target.replace(/\.[a-z0-9]+$/i, "") + ".lrc");
  } catch (e) {}
  // 同名 .jpg 封面 sidecar 一起移动（存在才移）
  try {
    var imgSrc = track.remote_path.replace(/\.[a-z0-9]+$/i, "") + ".jpg";
    var stI = await statRemote(imgSrc);
    if (stI) await moveRemote(imgSrc, target.replace(/\.[a-z0-9]+$/i, "") + ".jpg");
  } catch (e) {}
  db.prepare("UPDATE tracks SET remote_path=?, status='imported', updated_at=? WHERE id=?").run(target, Date.now(), trackId);
  // 关键时序：等待 rclone 视图刷新完成，后续 Navidrome 扫描才能看到新文件
  try { await refreshDir("music/_incoming"); } catch (e) {}
  try { await refreshDir("music", true); } catch (e) {}
  await sleep(2500);
  var updated = db.prepare("SELECT * FROM tracks WHERE id=?").get(trackId);
  // 自动刮削（可选，失败不阻塞）
  var autoScrape = cfg("auto_scrape", false);
  if (autoScrape === true || autoScrape === "true") {
    try {
      var r = await mtScrapeTrack(updated);
      if (!r.ok) logEvent("scrape_warn", updated.title + ": " + (r.reason || JSON.stringify(r.detail || {}).slice(0, 100)));
    } catch (e) { logEvent("scrape_warn", String(e.message || e)); }
  }
  logEvent("organize", trackId + " -> " + target);
  return updated;
}

export async function organizeAllPending() {
  var db = getDb();
  var rows = db.prepare("SELECT * FROM tracks WHERE status='uploaded'").all();
  var res = { ok: 0, failed: 0, errors: [] };
  for (var t of rows) {
    try { await organizeTrack(t.id); res.ok++; }
    catch (e) { res.failed++; res.errors.push(t.title + ": " + String(e.message || e)); }
  }
  return res;
}
