import { sleep } from "../util.js";

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
