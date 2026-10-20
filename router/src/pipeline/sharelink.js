import { getDb, logEvent } from "../db/index.js";
import { cfg } from "../config.js";
import { newId, fetchWithTimeout } from "../util.js";
import { enqueueDownload } from "../downloader/queue.js";

var UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

function playTime(sec) { sec = Number(sec) || 0; return Math.floor(sec / 60) + ":" + String(sec % 60).padStart(2, "0"); }

// 下载/暂存目录名（属于"存放在哪"，与专辑无关）
function pickShareUser(link) {
  var by = String(cfg("share_album_by", "user"));
  return by === "playlist" ? (link.title || "分享歌单") : (link.share_user || link.title || "分享歌单");
}

/**
 * v1.9.0：写入 tags 的 album 值。
 *
 * 旧行为把 album 直接等同于 folder（= 分享者昵称），导致整个歌单 100+ 首的 album 全变成
 * "demo" 这种伪专辑名：App 的「专辑」页只剩一张巨卡、且与歌单页内容完全重复。
 *
 * 默认返回 null，交给 runner 使用曲目自身的真实专辑（song.album / al.name）；
 * 只有显式配置 share_album_by=playlist 或 user 时才回退到歌单名/分享者名。
 */
function pickShareAlbum(link) {
  var by = String(cfg("share_album_by", "track")).toLowerCase();
  if (by === "playlist") return link.title || "分享歌单";
  if (by === "user") return link.share_user || link.title || "分享歌单";
  return null;
}

// ---------- URL 解析（含分享者 userid）----------
// 从任意分享文本中提取第一个 http(s) 链接（App 分享常带中文描述/换行/表情）
function extractUrl(text) {
  var m = /https?:\/\/[^\s\u3000-\u303f\uff00-\uffef\u4e00-\u9fff，。、；：“”‘’『』【】（）<>"'`]+/i.exec(String(text || ""));
  return m ? m[0].replace(/[.,;:，。、；！？]+$/, "") : "";
}

export async function parseShareUrl(raw) {
  raw = String(raw || "").trim();
  var url = extractUrl(raw) || raw;
  var m, out = { platform: "", external_id: "", user_id: "" };
  if ((m = /user(?:id)?=(\d{4,})/i.exec(url))) out.user_id = m[1];
  // 网易云（域名限定，防误伤 QQ）
  if ((m = /music\.163\.com\/[^\s]*[?&](?:id|sid)=(\d+)/.exec(url)) || (m = /music\.163\.com\/(?:m\/)?(?:#\/)?playlist[?\/=#]*(?:id=)?(\d{5,})/.exec(url))) {
    out.platform = "wy"; out.external_id = m[1]; return out;
  }
  // QQ音乐
  if ((m = /y\.qq\.com\/[\s\S]*?playlist\/(\d{5,})/.exec(url)) || (m = /disstid=(\d{5,})/i.exec(url)) || (m = /y\.qq\.com\/v\/(?:channel\/[A-Za-z]+\/)?(\d{5,})/.exec(url))) {
    out.platform = "tx"; out.external_id = m[1]; return out;
  }
  // 短链（163cn.tv / url.gtimg / c19 等）跟随重定向 + 页面内容兜底
  if (/^https?:\/\//i.test(url)) {
    var html = "";
    try {
      var r = await fetchWithTimeout(url, { headers: { "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Version/16.0 Mobile/15E148 Safari/604.1" } }, 15000);
      var fin = String(r.url || "");
      try { html = (await r.text()).slice(0, 300000); } catch (e) {}
      var hay = fin + "\n" + html;
      if (!out.user_id && (m = /user(?:id)?=(\d{4,})/i.exec(hay) || /"(?:user_?[iI]d|userId|userid)"\s*:\s*"?(\d{4,})"/.exec(hay))) out.user_id = m[1];
      if (/music\.163|163cn\.tv/.test(hay) && (m = /music\.163\.com\/[^\s"'<]*?[?&]id=(\d+)/.exec(hay) || /"playlist[?&]id=(\d+)"/.exec(hay) || /"id":\s*(\d{6,})/.exec(hay) || /songlist[\s\S]{0,200}?"creatorId/.test(hay) ? /music\.163\.com\/[^"']*?[?&]id=(\d+)/.exec(hay) : null)) { out.platform = "wy"; out.external_id = m[1]; return out; }
      if (/y\.qq\.com/.test(hay) && (m = /playlist\/(\d{5,})/.exec(hay) || /disstid[=:"']+(\d{5,})/i.exec(hay))) { out.platform = "tx"; out.external_id = m[1]; return out; }
      // 重定向终点通用判定
      if (/music\.163\.com/.test(fin) && (m = /[?&](?:id|sid)=(\d+)/.exec(fin))) { out.platform = "wy"; out.external_id = m[1]; return out; }
    } catch (e) {
      throw new Error("短链解析失败: " + String(e.message || e).slice(0, 80));
    }
  }
  throw new Error("未识别到歌单链接：请粘贴网易云/QQ音乐歌单的完整分享链接（如 https://music.163.com/playlist?id=… 或 https://y.qq.com/n/ryqq/playlist/…）");
}

// ---------- 分享者昵称（SSR 反查）----------
// 移动端 SSR 页面反查用户昵称（实测匿名可用，无需 cookie）
async function wyNicknameByUid(uid) {
  if (!uid) return "";
  try {
    var r = await fetchWithTimeout("https://music.163.com/m/user/home?id=" + uid, { headers: { "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1", Referer: "https://music.163.com/" } }, 15000);
    var h = await r.text();
    var m = /"nickname":\s*"((?:[^"\\]|\\.)+)"/.exec(h) || /<title>([^<]{1,50}?)的个人主页/.exec(h);
    if (!m) return "";
    var raw = m[1];
    try { if (raw.indexOf("\\u") >= 0) raw = JSON.parse('"' + raw + '"'); } catch (e) {}
    return String(raw).trim();
  } catch (e) { return ""; }
}

// ---------- 拉取歌单 ----------
export async function fetchPlaylist(platform, id) {
  if (platform === "wy") return wyPlaylist(id);
  return txPlaylist(id);
}

async function wyPlaylist(id) {
  var r = await fetchWithTimeout("https://music.163.com/api/v6/playlist/detail?id=" + id + "&n=1000", { headers: { Referer: "https://music.163.com/", "User-Agent": UA } }, 20000);
  var j = await r.json().catch(function () { return null; });
  var pl = (j && (j.playlist || j.result)) || null;
  if (!pl || !pl.name) {
    r = await fetchWithTimeout("https://music.163.com/api/playlist/detail?id=" + id, { headers: { Referer: "https://music.163.com/", "User-Agent": UA } }, 20000);
    j = await r.json().catch(function () { return null; });
    pl = j && (j.result || j.playlist);
  }
  if (!pl || !pl.name) throw new Error("网易歌单获取失败（可能已删除/转为私密）");
  var ids = (pl.trackIds || []).map(function (x) { return String(typeof x === "object" ? x.id : x); });
  if (!ids.length && (pl.tracks || []).length) ids = pl.tracks.map(function (x) { return String(x.id); });
  if (pl.trackCount && ids.length > pl.trackCount) ids = ids.slice(0, pl.trackCount);
  var songs = [];
  for (var i = 0; i < ids.length; i += 40) {
    var chunk = ids.slice(i, i + 40);
    try {
      var rr = await fetchWithTimeout("https://music.163.com/api/song/detail?ids=" + encodeURIComponent("[" + chunk.join(",") + "]"), { headers: { Referer: "https://music.163.com/", "User-Agent": UA, Cookie: "appver=8.9.7;" } }, 20000);
      var jj = await rr.json();
      ((jj && jj.songs) || []).forEach(function (s) {
        var ar = (s.artists || s.ar || []).map(function (a) { return a.name; }).join("、");
        var al = s.album || s.al || {};
        var types = [{ type: "flac", size: "" }, { type: "320k", size: "" }, { type: "128k", size: "" }];
        songs.push({
          source: "wy", id: String(s.id), name: s.name || "", singer: ar,
          album: al.name || "", duration: Math.floor((s.duration || s.dt || 0) / 1000),
          img: al.picUrl || null, types: types,
          raw: { singer: ar, name: s.name, albumName: al.name || "", albumId: al.id, source: "wy", interval: playTime(Math.floor((s.duration || s.dt || 0) / 1000)), songmid: s.id, img: al.picUrl || null, lrc: null, types: types, _types: {}, typeUrl: {} },
        });
      });
    } catch (e) { logEvent("share_warn", "wy detail: " + String(e.message).slice(0, 60)); }
  }
  return { platform: "wy", title: pl.name || String(id), creator: (pl.creator && pl.creator.nickname) || "网易歌单", songs: songs };
}

async function txPlaylist(id) {
  var r = await fetchWithTimeout("https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&disstid=" + id + "&format=json&n=2000", { headers: { Referer: "https://y.qq.com/", "User-Agent": UA } }, 20000);
  var j = await r.json().catch(function () { return null; });
  var dd = j && j.cdlist && j.cdlist[0];
  if (!dd || !dd.dissname) throw new Error("QQ歌单获取失败（可能已删除/设为私密）");
  var songs = [];
  (dd.songlist || []).forEach(function (s) {
    var singerArr = (s.singer || []).map(function (x) { return x.name; }).join("、");
    var albumMid = s.albummid || "";
    var img = albumMid ? "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg" : "";
    var types = [];
    if (s.sizeflac || s.sizeape) types.push({ type: "flac", size: "" });
    types.push({ type: "320k", size: "" }, { type: "128k", size: "" });
    songs.push({
      source: "tx", id: String(s.songmid || ""), name: s.songname || "", singer: singerArr,
      album: s.albumname || "", duration: s.interval || 0, img: img, types: types,
      raw: { singer: singerArr, name: s.songname, albumName: s.albumname || "", albumId: albumMid, source: "tx", interval: playTime(s.interval || 0), songId: s.songid, albumMid: albumMid, strMediaMid: s.strMediaMid || s.mid || "", songmid: s.songmid, img: img, lrc: null, types: types, _types: {}, typeUrl: {} },
    });
  });
  return { platform: "tx", title: dd.dissname || String(id), creator: dd.nickname || "QQ歌单", songs: songs };
}

// ---------- 预览（不下载）----------
export async function prepareShareLink(url, quality, nameOverride) {
  var parsed = await parseShareUrl(url);
  var db = getDb();
  var info = await fetchPlaylist(parsed.platform, parsed.external_id);
  var uid = parsed.user_id || "";
  var nick = uid ? (parsed.platform === "wy" ? await wyNicknameByUid(uid) : "") : "";
  var shareUser = String(nameOverride || "").trim() || nick || info.creator;
  var existing = db.prepare("SELECT id FROM sharelinks WHERE platform=? AND external_id=?").get(parsed.platform, parsed.external_id);
  var lid = existing ? existing.id : newId();
  if (existing) {
    db.prepare("UPDATE sharelinks SET title=?, share_user=?, user_id=?, quality=?, url=? WHERE id=?").run(info.title, shareUser, uid, quality || String(cfg("default_quality", "auto")), String(url), lid);
  } else {
    db.prepare("INSERT INTO sharelinks(id,url,platform,external_id,title,share_user,quality,status,song_count,added_at,last_sync_at,last_result,user_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")
      .run(lid, String(url), parsed.platform, parsed.external_id, info.title, shareUser, quality && ["auto","flac","320k","128k"].indexOf(String(quality)) >= 0 ? String(quality) : String(cfg("default_quality", "auto")), "pending", info.songs.length, Date.now(), 0, "待确认（预览已生成）", uid);
  }
  // 曲目清单写入 items（state=preview），已有记录保留其状态
  var inLib = {};
  db.prepare("SELECT ext_key FROM sharelink_items WHERE link_id=? AND state IN ('done','exists','queued')").all(lid).forEach(function (x) { inLib[x.ext_key] = 1; });
  var dupStmt = db.prepare("SELECT id FROM tracks WHERE platform=? AND song_id=? AND status IN ('uploaded','imported')");
  for (var i = 0; i < info.songs.length; i++) {
    var s = info.songs[i];
    var extKey = s.source + ":" + s.id;
    var known = db.prepare("SELECT state, song_json FROM sharelink_items WHERE link_id=? AND ext_key=?").get(lid, extKey);
    var globalDup = dupStmt.get(s.source, String(s.id)) ? 1 : 0;
    var songJson = JSON.stringify(s);
    if (known) {
      db.prepare("UPDATE sharelink_items SET no=?, title=?, artist=?, song_json=?, in_lib=? WHERE link_id=? AND ext_key=?").run(i + 1, s.name, s.singer, songJson, globalDup, lid, extKey);
    } else {
      db.prepare("INSERT INTO sharelink_items(link_id,ext_key,title,artist,no,task_id,track_id,state,added_at,song_json,in_lib) VALUES(?,?,?,?,?,?,?,?,?,?,?)")
        .run(lid, extKey, s.name, s.singer, i + 1, "", "", inLib[extKey] ? "queued" : "preview", Date.now(), songJson, globalDup);
    }
  }
  db.prepare("UPDATE sharelinks SET song_count=? WHERE id=?").run(info.songs.length, lid);
  logEvent("share_preview", info.title + " (" + shareUser + ", " + info.songs.length + "首)" + (existing ? " [已存在,刷新]" : ""));
  return { id: lid, title: info.title, share_user: shareUser, creator: info.creator, user_id: uid, platform: parsed.platform, count: info.songs.length, status: existing ? (db.prepare("SELECT status FROM sharelinks WHERE id=?").get(lid).status) : "pending" };
}

export function getSharePreview(id) {
  var db = getDb();
  var link = db.prepare("SELECT * FROM sharelinks WHERE id=?").get(id);
  if (!link) throw new Error("链接不存在");
  var items = db.prepare("SELECT no,ext_key,title,artist,state,in_lib,task_id,track_id FROM sharelink_items WHERE link_id=? ORDER BY no").all(id);
  return { link: { id: link.id, title: link.title, share_user: link.share_user, creator: link.creator_name || "", user_id: link.user_id, platform: link.platform, status: link.status, quality: link.quality, song_count: link.song_count, last_result: link.last_result }, songs: items };
}

// ---------- 确认下载（预览 -> 入队）----------
export async function commitShareLink(id, opts) {
  opts = opts || {};
  var db = getDb();
  var link = db.prepare("SELECT * FROM sharelinks WHERE id=?").get(id);
  if (!link) throw new Error("链接不存在");
  if (opts.share_user && opts.share_user !== link.share_user) {
    db.prepare("UPDATE sharelinks SET share_user=? WHERE id=?").run(String(opts.share_user).trim(), id);
    link.share_user = String(opts.share_user).trim();
  }
  var quality = opts.quality && ["auto", "flac", "320k", "128k"].indexOf(String(opts.quality)) >= 0 ? String(opts.quality) : (link.quality || String(cfg("default_quality", "auto")));
  db.prepare("UPDATE sharelinks SET quality=?, status='active' WHERE id=?").run(quality, id);
  var folder = pickShareUser(link);
  var rows = db.prepare("SELECT * FROM sharelink_items WHERE link_id=? AND state='preview' ORDER BY no").all(id);
  if (opts.keys && opts.keys.length) {
    var set2 = {};
    opts.keys.forEach(function (k) { set2[k] = 1; });
    rows = rows.filter(function (x) { return set2[x.ext_key]; });
  }
  var n = 0;
  for (var it of rows) {
    var song = {};
    try { song = JSON.parse(it.song_json || "{}"); } catch (e) {}
    if (!song.id) continue;
    if (it.in_lib) { db.prepare("UPDATE sharelink_items SET state='exists' WHERE link_id=? AND ext_key=?").run(id, it.ext_key); continue; }
    var share = { folder: folder, album: pickShareAlbum(link), no: it.no, link_id: id, playlist: link.title };
    var taskId = enqueueDownload(song, quality, "", share);
    db.prepare("UPDATE sharelink_items SET state='queued', task_id=?, added_at=? WHERE link_id=? AND ext_key=?").run(taskId, Date.now(), id, it.ext_key);
    n++;
  }
  db.prepare("UPDATE sharelinks SET last_sync_at=?, last_result=? WHERE id=?").run(Date.now(), "确认下载 " + n + " 首（共 " + link.song_count + "）", id);
  logEvent("share_commit", link.title + ": 入队 " + n + " 首");
  return { ok: true, queued: n, quality: quality, share_user: link.share_user };
}

// ---------- 定时同步：仅处理已确认订阅 ----------
export async function syncShareLink(linkId, opts) {
  opts = opts || {};
  var db = getDb();
  var link = db.prepare("SELECT * FROM sharelinks WHERE id=?").get(linkId);
  if (!link) throw new Error("链接不存在");
  var info;
  try {
    info = await fetchPlaylist(link.platform, link.external_id);
  } catch (e) {
    var fc = (link.fail_count || 0) + 1;
    var st = fc >= 5 ? "expired" : link.status;
    db.prepare("UPDATE sharelinks SET fail_count=?, last_sync_at=?, last_result=?, status=? WHERE id=?").run(fc, Date.now(), "解析失败: " + String(e.message || e).slice(0, 100), st, linkId);
    logEvent("share", link.title + " 解析失败(" + fc + "): " + String(e.message || e).slice(0, 80));
    return { ok: false, error: String(e.message || e), fail_count: fc, status: st };
  }
  db.prepare("UPDATE sharelinks SET title=?, fail_count=0, status=CASE WHEN status='expired' THEN 'active' ELSE status END WHERE id=?").run(info.title, linkId);
  db.prepare("UPDATE sharelink_items SET state='failed' WHERE link_id=? AND state='queued' AND task_id != '' AND EXISTS (SELECT 1 FROM tasks WHERE tasks.id = sharelink_items.task_id AND tasks.status IN ('failed','cancelled'))").run(linkId);
  var added = 0, exists = 0, skipped = 0;
  var folder = pickShareUser(link);
  var quality = link.quality || String(cfg("default_quality", "auto"));
  for (var i = 0; i < info.songs.length; i++) {
    var s = info.songs[i];
    var extKey = s.source + ":" + s.id;
    var known = db.prepare("SELECT * FROM sharelink_items WHERE link_id=? AND ext_key=?").get(linkId, extKey);
    if (known && (known.state === "done" || known.state === "exists" || known.state === "queued")) { skipped++; continue; }
    if (known && known.state === "preview") { skipped++; continue; } // 预览中未确认的不动
    var globalDup = db.prepare("SELECT id FROM tracks WHERE platform=? AND song_id=? AND status IN ('uploaded','imported')").get(s.source, String(s.id));
    if (globalDup && !opts.force) {
      db.prepare("INSERT OR REPLACE INTO sharelink_items(link_id,ext_key,title,artist,no,task_id,track_id,state,added_at,song_json,in_lib) VALUES(?,?,?,?,?,?,?,?,?,?,?)").run(linkId, extKey, s.name, s.singer, i + 1, "", globalDup.id, "exists", Date.now(), JSON.stringify(s), 1);
      exists++;
      continue;
    }
    var share = { folder: folder, album: pickShareAlbum(info), no: i + 1, link_id: linkId, playlist: info.title };
    var taskId = enqueueDownload(s, quality, "", share);
    db.prepare("INSERT OR REPLACE INTO sharelink_items(link_id,ext_key,title,artist,no,task_id,track_id,state,added_at,song_json,in_lib) VALUES(?,?,?,?,?,?,?,?,?,?,?)").run(linkId, extKey, s.name, s.singer, i + 1, taskId, "", "queued", Date.now(), JSON.stringify(s), 0);
    added++;
  }
  var summary = "同步: 补下 " + added + " 首，已在库 " + exists + "，共 " + info.songs.length + " 首";
  db.prepare("UPDATE sharelinks SET song_count=?, last_sync_at=?, last_result=? WHERE id=?").run(info.songs.length, Date.now(), summary, linkId);
  logEvent("share", link.title + " " + summary);
  return { ok: true, added: added, exists: exists, total: info.songs.length, title: info.title, share_user: link.share_user };
}

// ---------- 兼容旧接口 ----------
export async function addShareLink(url, quality) {
  var p = await prepareShareLink(url, quality);
  return p; // 不再自动下载；commit 才下载
}

var schedTimer = null;
export function startShareScheduler(broadcast) {
  if (schedTimer) return;
  async function tick() {
    if (String(cfg("share_auto_sync", true)) === "false") return;
    var hours = Math.max(1, Number(cfg("share_sync_hours", 24)) || 24);
    var due = getDb().prepare("SELECT id FROM sharelinks WHERE status='active' AND (? - last_sync_at) > ?").all(Date.now(), hours * 3600000);
    for (var l of due) {
      try {
        var r = await syncShareLink(l.id);
        if (broadcast && r.ok && r.added) broadcast("share_sync", { id: l.id, added: r.added });
      } catch (e) {}
      await new Promise(function (x) { setTimeout(x, 3000); });
    }
  }
  schedTimer = setInterval(function () { tick().catch(function () {}); }, 3600000);
  setTimeout(function () { tick().catch(function () {}); }, 90000);
}
