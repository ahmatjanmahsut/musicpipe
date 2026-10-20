import { fetchWithTimeout } from "../util.js";

// 尽力而为获取歌词 LRC 文本；失败返回空串（由 Music Tag Web 后续补全）
export async function getLyric(platform, song) {
  try {
    if (platform === "wy") {
      var r = await fetchWithTimeout("https://music.163.com/api/song/lyric?id=" + song.id + "&lv=-1&kv=-1&tv=-1", { headers: { Referer: "https://music.163.com", "User-Agent": "Mozilla/5.0" } }, 12000);
      var j = await r.json();
      return (j && j.lrc && j.lrc.lyric) || "";
    }
    if (platform === "tx") {
      var r2 = await fetchWithTimeout("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=" + song.id + "&gtp=197&format=json&nobase64=1&ct=20&cv=1006", { headers: { Referer: "https://y.qq.com/portal/player.html", "User-Agent": "Mozilla/5.0" } }, 12000);
      var text = await r2.text();
      var m = /\w+\((.*)\)$/.exec(text.trim());
      var body = m ? JSON.parse(m[1]) : JSON.parse(text);
      var lrc = body.lyric || "";
      if (!/\[\d+:\d+/.test(lrc) && /^[A-Za-z0-9+/=\s]{20,}$/.test(lrc)) {
        try { lrc = Buffer.from(lrc, "base64").toString("utf8"); } catch (e) {}
      }
      return lrc;
    }
    if (platform === "kw") {
      var r3 = await fetchWithTimeout("http://mobile.kuwo.cn/mpage/html5/songinfoandlrc?mid=" + song.id + "&flag=0", { headers: { "User-Agent": "Mozilla/5.0" } }, 12000);
      var j3 = await r3.json().catch(function () { return null; });
      if (j3 && j3.result && j3.result.content) {
        var content = j3.result.content;
        if (typeof content === "string") return content;
        return content.lrc || content.lyric || "";
      }
      return "";
    }
    if (platform === "kg") {
      var r4 = await fetchWithTimeout("https://krcs.kugou.com/lyrics/get?format=json&apikey=66c47b4b09088de3f2d3b19d9a912461&client=pc&keyword=" + encodeURIComponent((song.singer + " - " + song.name)) + "&mid=&hash=" + song.id + "&accesskey=" + cryptoAccessKey(), {}, 12000);
      var j4 = await r4.json().catch(function () { return null; });
      var cnt = j4 && j4.content;
      if (cnt) {
        var buf = Buffer.from(cnt, "base64");
        try {
          var zlib = await import("node:zlib");
          buf = zlib.inflateSync(buf.subarray(4));
        } catch (e) { return ""; }
        return decryptKrc(buf);
      }
    }
  } catch (e) { return ""; }
  return "";
}

var KEY_TAB = [64, 71, 94, 108, 53, 63, 68, 81, 88, 76, 74, 66, 103, 93, 85, 98];
function cryptoAccessKey() { return "66c47b4b09088de3f2d3b19d9a912461"; }
function decryptKrc(buf) {
  // krc: 每字节 ^ KEY_TAB[i%16]，UTF-8
  var out = Buffer.alloc(buf.length);
  for (var i = 0; i < buf.length; i++) out[i] = buf[i] ^ KEY_TAB[i % 16];
  try {
    var text = out.toString("utf8");
    var json = JSON.parse(text);
    return (json.content || []).map(function (l) { return l.lineText; }).join("\n");
  } catch (e) { return ""; }
}
