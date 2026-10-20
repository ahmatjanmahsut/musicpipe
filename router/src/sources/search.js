import crypto from "node:crypto";
import { fetchWithTimeout } from "../util.js";

export function decodeName(str) {
  if (str === null || str === undefined) return "";
  return String(str)
    .replace(/&quot;/g, '"').replace(/&apos;/g, "'")
    .replace(/&#x([0-9a-fA-F]+);/g, function (m, h) { return String.fromCodePoint(parseInt(h, 16)); })
    .replace(/&#(\d+);/g, function (m, d) { return String.fromCodePoint(parseInt(d, 10)); })
    .replace(/\\u([0-9a-fA-F]{4})/g, function (m, h) { return String.fromCharCode(parseInt(h, 16)); })
    .replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&");
}

function sizeFormate(bytes) {
  if (!bytes) return "0B";
  var i = Math.floor(Math.log(bytes) / Math.log(1024));
  return (bytes / Math.pow(1024, i)).toFixed(i ? 1 : 0) + ["B", "KB", "MB", "GB"][i];
}
function playTime(sec) { sec = Number(sec) || 0; return Math.floor(sec / 60) + ":" + String(sec % 60).padStart(2, "0"); }
function formatSingerName(singers, key) {
  if (Array.isArray(singers)) return decodeName(singers.map(function (s) { return s[key || "name"]; }).filter(Boolean).join("、"));
  return decodeName(String(singers || ""));
}

function parseLooseJson(text) {
  try { return JSON.parse(text); } catch (e) {
    var fixed = String(text)
      .replace(/([{,]\s*)([A-Za-z_$][A-Za-z0-9_$]*)\s*:/g, '$1"$2":')
      .replace(/'([^'\\]*(?:\\.[^'\\]*)*)'/g, function (m0, inner) { return '"' + inner.replace(/"/g, '\\"') + '"'; })
      .replace(/,\s*([}\]])/g, "$1");
    return JSON.parse(fixed);
  }
}

// ---------- kw ----------
async function kwSearch(keyword, page, limit) {
  var url = "http://search.kuwo.cn/r.s?client=kt&all=" + encodeURIComponent(keyword) + "&pn=" + (page - 1) + "&rn=" + limit + "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1";
  var r = await fetchWithTimeout(url, { headers: { "User-Agent": "Mozilla/5.0" } }, 15000);
  var text = await r.text();
  var body = parseLooseJson(text);
  var rawList = (body && (body.abslist || body.musicList)) || [];
  var list = [];
  for (var info of rawList) {
    var songId = String(info.MUSICRID || "").replace("MUSIC_", "");
    var types = [];
    if (info.N_MINFO) {
      for (var part of String(info.N_MINFO).split(";")) {
        var mm = part.match(/level:(\w+),bitrate:(\d+),format:(\w+),size:([\w.]+)/);
        if (!mm) continue;
        if (mm[2] === "4000") types.push({ type: "flac24bit", size: mm[4] });
        else if (mm[2] === "2000") types.push({ type: "flac", size: mm[4] });
        else if (mm[2] === "320") types.push({ type: "320k", size: mm[4] });
        else if (mm[2] === "128") types.push({ type: "128k", size: mm[4] });
      }
      types.reverse();
    }
    if (!types.length) types = [{ type: "128k", size: "" }, { type: "320k", size: "" }];
    var singerRaw = decodeName(info.ARTIST || "");
    list.push({
      source: "kw", id: songId,
      name: decodeName(info.SONGNAME || ""),
      singer: singerRaw.replace(/\(([^)]*)\)/g, "").replace(/\./g, ""),
      album: decodeName(info.ALBUM || ""),
      duration: parseInt(info.DURATION) || 0,
      types: types, img: null,
      raw: {
        name: decodeName(info.SONGNAME || ""), singer: singerRaw, source: "kw",
        songmid: songId, albumId: decodeName(info.ALBUMID || ""), albumName: decodeName(info.ALBUM || ""),
        interval: playTime(parseInt(info.DURATION) || 0), types: types, _types: {}, typeUrl: {},
      },
    });
  }
  return { list: list, total: parseInt(body && body.TOTAL) || list.length, allPage: Math.ceil((parseInt(body && body.TOTAL) || 0) / limit) };
}

// ---------- kg ----------
async function kgSearch(keyword, page, limit) {
  var url = "http://songsearch.kugou.com/song_search_v2?platform=AndroidFilter&iscorrection=1&keyword=" + encodeURIComponent(keyword) + "&hifiquality=0&pagesize=" + limit + "&PrivilegeFilter=0&page=" + page;
  var r = await fetchWithTimeout(url, { headers: { "User-Agent": "Mozilla/5.0" } }, 15000);
  var body = await r.json().catch(function () { return null; });
  if (!body || body.error_code !== 0) throw new Error("kg 搜索接口异常");
  var raws = (body.data && body.data.lists) || [];
  var list = [];
  function filterData(d) {
    var types = [];
    if (d.FileSize) types.push({ type: "128k", size: sizeFormate(d.FileSize), hash: d.FileHash });
    if (d.HQFileSize) types.push({ type: "320k", size: sizeFormate(d.HQFileSize), hash: d.HQFileHash });
    if (d.SQFileSize) types.push({ type: "flac", size: sizeFormate(d.SQFileSize), hash: d.SQFileHash });
    if (d.ResFileSize) types.push({ type: "flac24bit", size: sizeFormate(d.ResFileSize), hash: d.ResFileHash });
    if (!types.length) types = [{ type: "128k", size: "", hash: d.FileHash }];
    return {
      source: "kg", id: String(d.FileHash || d.Audioid),
      name: decodeName(d.OriSongName || d.SongName || ""),
      singer: formatSingerName(d.Singers, "name"),
      album: decodeName(d.AlbumName || ""),
      duration: d.Duration || 0, img: null,
      raw: {
        singer: formatSingerName(d.Singers, "name"), name: decodeName((d.OriSongName || "") + (d.Suffix ? " " + d.Suffix : "")),
        albumName: decodeName(d.AlbumName || ""), albumId: d.AlbumID, songmid: d.Audioid, albumAudioId: d.MixSongID,
        source: "kg", interval: playTime(d.Duration || 0), img: null, lrc: null, otherSource: null,
        hash: d.FileHash, types: types, _types: {}, typeUrl: {},
      },
    };
  }
  for (var d of raws) {
    list.push(filterData(d));
    if (Array.isArray(d.Grp)) d.Grp.forEach(function (g) { list.push(filterData(g)); });
  }
  return { list: list, total: (body.data && body.data.total) || list.length, allPage: Math.ceil(((body.data && body.data.total) || 0) / limit) };
}

// ---------- tx ----------
// 使用稳定开放的旧搜索接口（musics.fcg 新版 sign 参数不稳定）
async function txSearch(keyword, page, limit) {
  var url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&p=" + page + "&perpage=" + limit + "&w=" + encodeURIComponent(keyword);
  var r = await fetchWithTimeout(url, { headers: { "User-Agent": "Mozilla/5.0", Referer: "https://y.qq.com/" } }, 15000);
  var body = await r.json().catch(function () { return null; });
  if (!body || body.code !== 0 || !body.data || !body.data.song) throw new Error("tx 搜索返回异常");
  var d = body.data.song;
  var rawList = d.list || [];
  var list = [];
  for (var item of rawList) {
    var file = { media_mid: item.media_mid || "" };
    var types = [];
    if (item.size_128mp3) types.push({ type: "128k", size: sizeFormate(item.size_128mp3) });
    if (item.size_320mp3) types.push({ type: "320k", size: sizeFormate(item.size_320mp3) });
    if (item.size_flac) types.push({ type: "flac", size: sizeFormate(item.size_flac) });
    if (item.size_new_hd) types.push({ type: "flac24bit", size: sizeFormate(item.size_new_hd) });
    if (!types.length) types = [{ type: "128k", size: "" }, { type: "320k", size: "" }];
    var albumMid = item.albummid || "";
    var singerArr = Array.isArray(item.singer) ? item.singer : [{ name: String(item.singer || "") }];
    var img = albumMid ? "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg" : (singerArr[0] && singerArr[0].mid ? "https://y.gtimg.cn/music/photo_new/T001R300x300M000" + singerArr[0].mid + ".jpg" : "");
    list.push({
      source: "tx", id: String(item.mid || item.songmid || ""),
      name: decodeName(item.name || item.songname || ""), singer: formatSingerName(singerArr, "name"),
      album: decodeName(item.albumname || ""), duration: item.interval || 0,
      img: img, types: types,
      raw: {
        singer: formatSingerName(singerArr, "name"), name: item.name || item.songname,
        albumName: item.albumname || "", albumId: albumMid, source: "tx",
        interval: playTime(item.interval || 0), songId: item.sid || item.id, albumMid: albumMid,
        strMediaMid: item.media_mid || "", songmid: item.mid, img: img,
        types: types, _types: {}, typeUrl: {},
      },
    });
  }
  return { list: list, total: d.totalnum || list.length, allPage: Math.ceil((d.totalnum || 0) / limit) };
}
// ---------- wy ----------
function eapi(url, object) {
  var text = typeof object === "object" ? JSON.stringify(object) : String(object);
  var message = "nobody" + url + "use" + text + "md5forencrypt";
  var digest = crypto.createHash("md5").update(message).digest("hex");
  var data = url + "-36cd479b6b5-" + text + "-36cd479b6b5-" + digest;
  var cipher = crypto.createCipheriv("aes-128-ecb", Buffer.from("e82ckenh8dichen8"), "");
  var params = Buffer.concat([cipher.update(Buffer.from(data)), cipher.final()]).toString("hex").toUpperCase();
  return { params: params };
}
async function wySearch(keyword, page, limit) {
  var form = eapi("/api/search/song/list/page", { keyword: keyword, needCorrect: "1", channel: "typing", offset: limit * (page - 1), scene: "normal", total: page === 1, limit: limit });
  var r = await fetchWithTimeout("http://interface.music.163.com/eapi/batch", {
    method: "post",
    headers: { "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36", origin: "https://music.163.com", "Content-Type": "application/x-www-form-urlencoded" },
    body: "params=" + encodeURIComponent(form.params),
  }, 15000);
  var body = await r.json().catch(function () { return null; });
  if (!body) throw new Error("wy 搜索无响应");
  var inner = body["*url*"] || body["/api/search/song/list/page"] || body.data || body;
  var resources = inner && inner.data && inner.data.resources;
  if (!resources && inner && inner.resources) resources = inner.resources;
  if (!resources) throw new Error("wy 搜索解析失败");
  var list = [];
  for (var wrap of resources) {
    var item = wrap.baseInfo ? wrap.baseInfo.simpleSongData : wrap;
    if (!item) continue;
    var types = [];
    var pr = item.privilege || {};
    if (pr.maxBrLevel === "hires") types.push({ type: "flac24bit", size: item.hr ? sizeFormate(item.hr.size) : "" });
    if (pr.maxbr >= 999000 && item.sq) types.push({ type: "flac", size: sizeFormate(item.sq.size) });
    if (pr.maxbr >= 320000 && item.h) types.push({ type: "320k", size: sizeFormate(item.h.size) });
    if (item.l) types.push({ type: "128k", size: sizeFormate(item.l.size) });
    if (!types.length) types = [{ type: "128k", size: "" }, { type: "320k", size: "" }];
    types.reverse();
    list.push({
      source: "wy", id: String(item.id),
      name: item.name || "", singer: (item.ar || []).map(function (a) { return a.name; }).join("、"),
      album: (item.al && item.al.name) || "", duration: Math.floor((item.dt || 0) / 1000),
      img: (item.al && item.al.picUrl) || null, types: types,
      raw: {
        singer: (item.ar || []).map(function (a) { return a.name; }).join("、"), name: item.name,
        albumName: (item.al && item.al.name) || "", albumId: item.al && item.al.id, source: "wy",
        interval: playTime(Math.floor((item.dt || 0) / 1000)), songmid: item.id, img: (item.al && item.al.picUrl) || null,
        lrc: null, types: types, _types: {}, typeUrl: {},
      },
    });
  }
  var total = (inner.data && inner.data.totalCount) || inner.totalCount || list.length;
  return { list: list, total: total, allPage: Math.ceil(total / limit) };
}

var IMPL = { kw: kwSearch, kg: kgSearch, tx: txSearch, wy: wySearch };
export var PLATFORMS = ["kw", "kg", "tx", "wy"];
export var PLATFORM_NAMES = { kw: "酷我", kg: "酷狗", tx: "QQ音乐", wy: "网易" };

export async function search(platform, keyword, page, limit) {
  var fn = IMPL[platform];
  if (!fn) throw new Error("不支持平台 " + platform);
  var r = await fn(keyword, page || 1, Math.min(limit || 30, 60));
  r.page = page || 1;
  r.platform = platform;
  return r;
}
