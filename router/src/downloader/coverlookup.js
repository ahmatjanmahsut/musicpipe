import { search } from "../sources/search.js";

/**
 * v1.9.1 封面查补：给"下载时拿不到封面地址"的曲目找一个可用的图片 URL。
 *
 * 为什么需要它：各平台返回的歌曲对象里只有网易云（wy）带 `img`（al.picUrl）。
 * kw / kg / tx 的 search 结果 img 恒为 null，于是这些平台下的歌既没有内嵌封面、
 * 也没有 .jpg sidecar，App 里就是一张白图（用户报的"很多封面空白"里有一部分就是它）。
 *
 * 策略：用"歌名 + 歌手"去网易云搜一次，只在**歌名归一化后完全相等、且歌手也对得上**时采用。
 * 宁可没有封面，也不给用户贴错封面 —— 一张错图比空白更难发现。
 */

/** 归一化：小写、去掉空白与常见标点/括号，只留文字与数字。 */
export function normName(s) {
  return String(s == null ? "" : s)
    .toLowerCase()
    .replace(/\s+/g, "")
    .replace(/[()（）\[\]【】{}<>《》"'\u2018\u2019\u201c\u201d,.，。!！?？、·\-_/\\|&+~@#$%^*=:;]/g, "");
}

var UNKNOWN_ARTISTS = ["未知歌手", "未知艺术家", "unknown", "unknownartist", ""];

function artistUsable(a) {
  var n = normName(a);
  return n !== "" && UNKNOWN_ARTISTS.indexOf(n) < 0;
}

/**
 * 按歌名+歌手反查封面 URL；查不到或不确信时返回 null（绝不抛异常）。
 * @param {string} title
 * @param {string} artist
 * @returns {Promise<string|null>}
 */
export async function lookupCoverUrl(title, artist) {
  var nt = normName(title);
  if (!nt) return null;
  var q = (String(title || "") + " " + String(artist || "")).trim();
  try {
    var r = await search("wy", q, 1, 10);
    var list = (r && r.list) || [];
    var wantArtist = artistUsable(artist);
    for (var i = 0; i < list.length; i++) {
      var s = list[i];
      if (!s || !s.img) continue;
      if (normName(s.name) !== nt) continue;
      if (wantArtist && normName(s.singer) !== normName(artist)) continue;
      return String(s.img);
    }
    return null;
  } catch (e) {
    return null;
  }
}
