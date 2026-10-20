import path from "node:path";
import { getSetting } from "./db/index.js";

export const ENV = {
  dataDir: process.env.MUSICPIPE_DATA || "/data/config",
  localDir: process.env.MUSICPIPE_LOCAL || "/data/local",
  musicMount: process.env.MUSICPIPE_MUSIC || "/music",
  rcloneRc: process.env.RCLONE_RC || "http://rclone-mount:5572",
  musictagUrl: process.env.MUSICTAG_URL || "http://music-tag-web:8002",
  navidromeUrl: process.env.NAVIDROME_URL || "http://navidrome:4533",
  port: Number(process.env.PORT || 7800),
};
ENV.stagingDir = path.join(ENV.localDir, "staging");
ENV.tmpDir = path.join(ENV.localDir, "tmp");

export function cfg(key, dflt) {
  var v = getSetting(key, null);
  return (v === null || v === undefined || v === "") ? dflt : v;
}
