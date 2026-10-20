import { sleep, fetchWithTimeout } from "../util.js";

var ORDER = ["flac24bit", "flac", "320k", "128k"];

export function planQualities(box, platform, want) {
  var declared = box.qualitys(platform) || [];
  var pool = declared.length ? ORDER.filter(function (q) { return declared.indexOf(q) >= 0; }) : ORDER.slice();
  var idx = pool.indexOf(want);
  if (idx > 0) return pool.slice(idx);
  if (idx === 0) return pool.slice();
  return pool.length ? pool : [want];
}

// box 返回 url 字符串
export async function getPlayUrl(box, platform, rawSong, quality) {
  var res = await box.dispatch(platform, "musicUrl", { type: quality, musicInfo: rawSong });
  var url = typeof res === "string" ? res : (res && typeof res.url === "string" ? res.url : null);
  if (!url || !/^https?:\/\//i.test(url)) throw new Error("音源返回的播放地址无效: " + String(url).slice(0, 120));
  return url;
}

export async function getPlayUrlWithFallback(box, platform, song, want) {
  var plans = planQualities(box, platform, want);
  var lastErr = null;
  for (var q of plans) {
    try {
      var url = await getPlayUrl(box, platform, song.raw || song, q);
      return { url: url, quality: q };
    } catch (e) {
      lastErr = e;
      await sleep(200);
    }
  }
  throw lastErr || new Error("无法获取播放地址（平台 " + platform + " 音质 " + want + "）");
}

/**
 * v1.6.0：嗅探音源返回链接的**真实容器格式**。
 *
 * 问题背景：音源在 want=flac 时有时会降级返回 mp3（或反过来宣称 mp3 却给 flac），
 * 而 runner 原先直接用"想要的音质"推导扩展名并写入 ID3 标签，于是落盘时会得到
 * 「文件头(mp3)与扩展名(flac)不符」——这既是校验失败的直接原因，也会把 ID3 写坏。
 *
 * 做法：带 Range: bytes=0-2047 只取文件头，按魔数判定。取不到就不判定
 * （返回 null），由调用方回退到按 quality 推导，绝不因为探测失败而中断下载。
 */
export async function sniffAudioType(url) {
  try {
    var r = await fetchWithTimeout(url, {
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MusicPipe/1.0",
        "Range": "bytes=0-2047"
      },
      redirect: "follow"
    }, 12000);
    var buf = Buffer.from(await r.arrayBuffer());
    return sniffBuffer(buf);
  } catch (e) {
    return null;
  }
}

/** 纯函数：按文件头魔数判定容器格式，便于单测。 */
export function sniffBuffer(buf) {
  if (!buf || buf.length < 4) return null;
  var magic = buf.toString("latin1");
  if (magic.slice(0, 4) === "fLaC") return "flac";
  if (magic.slice(0, 3) === "ID3") return "mp3";
  if (magic.slice(0, 4) === "OggS") return "ogg";
  if (magic.slice(0, 4) === "RIFF") return "wav";
  if (magic.slice(4, 8) === "ftyp") return "m4a";
  if (buf[0] === 0xFF && (buf[1] & 0xE0) === 0xE0) return "mp3";
  return null;
}
