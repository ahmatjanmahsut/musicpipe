import fs from "node:fs";
import path from "node:path";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import nodeid3 from "node-id3";
import { ENV } from "../config.js";
import { newId, fetchWithTimeout } from "../util.js";

var execFileP = promisify(execFile);

async function downloadTo(url, ext) {
  if (!url || !/^https?:/i.test(url)) return null;
  var dest = path.join(ENV.tmpDir, newId() + ext);
  try {
    var r = await fetchWithTimeout(url, { headers: { Referer: "https://music.163.com/", "User-Agent": "Mozilla/5.0" } }, 20000);
    if (!r.ok) return null;
    var buf = Buffer.from(await r.arrayBuffer());
    if (buf.length < 100) return null;
    fs.writeFileSync(dest, buf);
    return dest;
  } catch (e) { return null; }
}

function pickCoverExt(ct, url) {
  if (ct && ct.includes("png")) return ".png";
  if (ct && ct.includes("webp")) return ".webp";
  if (/\.(png|webp)/i.test(url || "")) return "." + RegExp.$1.toLowerCase();
  return ".jpg";
}

// meta: { title, artist, album, year?, genre?, lyrics, coverUrl, coverBuf }
export async function writeTags(filePath, ext, meta) {
  var coverPath = null;
  try {
    if (ext === "mp3") {
      var tags = { title: meta.title || "", artist: meta.artist || "", album: meta.album || "" };
      if (meta.albumArtist) tags.albumArtist = String(meta.albumArtist);
      if (meta.trackNumber) tags.trackNumber = String(meta.trackNumber);
      if (meta.year) tags.year = String(meta.year);
      if (meta.lyrics) tags.lyrics = meta.lyrics;
      if (meta.coverUrl || meta.coverBuf) {
        coverPath = meta.coverBuf ? await (async function () { var p = path.join(ENV.tmpDir, newId() + ".jpg"); fs.writeFileSync(p, meta.coverBuf); return p; })() : await downloadTo(meta.coverUrl, ".jpg");
        if (coverPath) tags.image = coverPath;
      }
      var res = nodeid3.write(tags, filePath);
      if (res && res.errors && res.errors.length) console.error("id3 warn:", res.errors);
    } else if (ext === "flac") {
      var args = ["--no-utf8-convert"];
      var setT = function (k, v) { if (v) args.push("--set-tag=" + k + "=" + String(v)); };
      setT("TITLE", meta.title); setT("ARTIST", meta.artist); setT("ALBUM", meta.album);
      setT("DATE", meta.year); setT("GENRE", meta.genre);
      setT("ALBUMARTIST", meta.albumArtist); setT("TRACKNUMBER", meta.trackNumber);
      if (meta.lyrics) setT("LYRICS", meta.lyrics);
      if (meta.coverUrl || meta.coverBuf) {
        coverPath = meta.coverBuf ? await (async function () { var p = path.join(ENV.tmpDir, newId() + ".jpg"); fs.writeFileSync(p, meta.coverBuf); return p; })() : await downloadTo(meta.coverUrl, ".jpg");
        if (coverPath) args.push("--import-picture-from=" + coverPath);
      }
      if (args.length) {
        args.push(filePath);
        await execFileP("metaflac", args, { timeout: 30000 });
      }
    }
    return { ok: true };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  } finally {
    if (coverPath) try { fs.unlinkSync(coverPath); } catch (e) {}
  }
}

export function checkMagic(filePath, expectExt) {
  try {
    var fd = fs.openSync(filePath, "r");
    var buf = Buffer.alloc(4);
    fs.readSync(fd, buf, 0, 4, 0);
    fs.closeSync(fd);
    var magic = buf.toString("latin1");
    var actual = null;
    if (magic === "fLaC") actual = "flac";
    else if (magic.slice(0, 3) === "ID3") actual = "mp3";
    else if (magic.slice(0, 4) === "OggS") actual = "ogg";
    else if (magic.slice(0, 4) === "FORM" || magic.slice(0, 4) === "RIFF") actual = "wav";
    else {
      // mp3 帧头 0xFF 0xFB/0xFA/0xF3/0xF2...
      if (buf[0] === 0xFF && (buf[1] & 0xE0) === 0xE0) actual = "mp3";
    }
    if (!actual && expectExt === "mp3") {
      // 有的 mp3 无 ID3，第一字节非 0xFF 则不可判，视为未知放行
      return { ok: true, magic: "unknown" };
    }
    if (!actual) return { ok: false, magic: "unknown", reason: "无法识别的音频文件头" };
    if (expectExt && actual !== expectExt && !(expectExt === "mp3" && actual === "mp3")) {
      return { ok: false, magic: actual, reason: "文件头(" + actual + ")与扩展名(" + expectExt + ")不符" };
    }
    return { ok: true, magic: actual };
  } catch (e) {
    return { ok: false, magic: "err", reason: String(e.message || e) };
  }
}
