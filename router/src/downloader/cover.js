import { fetchWithTimeout } from "../util.js";

// v6 封面 sidecar：与歌词 .lrc 相同的存储/调用方式（音频同名 .jpg 存网盘）。
// 本模块提供两个纯函数式工具：图片魔数嗅探 + 尽力而为的封面下载。

/** 按魔数嗅探图片 MIME；未知返回 null。sidecar 一律存 .jpg 扩展名，服务时按真实格式回 Content-Type。 */
export function sniffImageType(buf) {
  if (!buf || buf.length < 12) return null;
  if (buf[0] === 0xff && buf[1] === 0xd8) return "image/jpeg";
  if (buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4e && buf[3] === 0x47) return "image/png";
  if (buf.slice(0, 4).toString("latin1") === "RIFF" && buf.slice(8, 12).toString("latin1") === "WEBP") return "image/webp";
  if (buf[0] === 0x47 && buf[1] === 0x49 && buf[2] === 0x46) return "image/gif";
  return null;
}

/** 下载封面图为 Buffer（带 Referer/UA，20s 超时，200B~8MB，魔数校验）；失败返回 null，绝不抛出。 */
export async function fetchCoverBuf(url, maxBytes) {
  try {
    if (!url || !/^https?:/i.test(String(url))) return null;
    var r = await fetchWithTimeout(String(url), {
      headers: { Referer: "https://music.163.com/", "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MusicPipe/1.0" }
    }, 20000);
    if (!r.ok) return null;
    var buf = Buffer.from(await r.arrayBuffer());
    if (buf.length < 200 || buf.length > (maxBytes || 8388608)) return null;
    if (!sniffImageType(buf)) return null;
    return buf;
  } catch (e) { return null; }
}
