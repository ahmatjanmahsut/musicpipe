import { getDb } from "../db/index.js";
import { cfg } from "../config.js";
import { newId, sleep } from "../util.js";
import { runTask } from "./runner.js";

var running = new Map();      // taskId -> {abort: fn, promise}
var kickResolvers = [];
var started = false;

export function broadcast(fn) { _broadcast = fn; }
var _broadcast = function () {};

export function notify(event, data) { try { _broadcast(event, data); } catch (e) {} }

export function enqueueDownload(song, quality, sourceId, share) {
  var id = newId();
  var now = Date.now();
  var payload = JSON.stringify({ song: song, quality: quality || String(cfg("default_quality", "320k")), source_id: sourceId, share: share || null });
  getDb().prepare("INSERT INTO tasks(id,type,status,payload,created_at,updated_at) VALUES(?,?,?,?,?,?)")
    .run(id, "download", "pending", payload, now, now);
  notify("task", { id: id, status: "pending", type: "download" });
  kick();
  return id;
}

export function retryTask(id) {
  var t = getDb().prepare("SELECT * FROM tasks WHERE id=?").get(id);
  if (!t) throw new Error("任务不存在");
  getDb().prepare("UPDATE tasks SET status='pending', error='', progress=0, retry=retry+1, updated_at=? WHERE id=?").run(Date.now(), id);
  notify("task", { id: id, status: "pending", type: t.type });
  kick();
}

export function cancelTask(id) {
  var r = running.get(id);
  if (r && r.abort) r.abort("cancelled");
  getDb().prepare("UPDATE tasks SET status='cancelled', updated_at=?, finished_at=? WHERE id=?").run(Date.now(), Date.now(), id);
  notify("task", { id: id, status: "cancelled" });
}

export function clearFinished() {
  getDb().prepare("DELETE FROM tasks WHERE status IN ('done','imported','cancelled')").run();
}

function kick() {
  var arr = kickResolvers;
  kickResolvers = [];
  arr.forEach(function (r) { r(); });
}

function waitKick() {
  return new Promise(function (resolve) { kickResolvers.push(resolve); });
}

export function updateTask(id, patch) {
  var sets = [], vals = [];
  Object.keys(patch).forEach(function (k) { sets.push(k + "=?"); vals.push(patch[k]); });
  sets.push("updated_at=?"); vals.push(Date.now());
  getDb().prepare("UPDATE tasks SET " + sets.join(",") + " WHERE id=?").run(...vals, id);
  getDb().prepare("SELECT * FROM tasks WHERE id=?").get(id);
  notify("task", getDb().prepare("SELECT * FROM tasks WHERE id=?").get(id));
}

function setStatus(id, status, extra) {
  updateTask(id, Object.assign({ status: status }, extra || {}));
}

export function poolSize() {
  var n = Number(cfg("dl_concurrency", 3));
  if (!(n >= 1)) n = 1;
  if (n > 8) n = 8;
  return Math.floor(n);
}

export async function startQueue() {
  if (started) return;
  started = true;
  // 恢复：running 状态重置 pending
  getDb().prepare("UPDATE tasks SET status='pending' WHERE status IN ('downloading','verifying','tagging','uploading','organizing')").run();
  var mainLoop = async function () {
    while (true) {
      try {
        var N = poolSize();
        while (running.size >= N) { await sleep(1000); }
        // 取 pending 任务
        var free = N - running.size;
        if (free > 0) {
          var rows = getDb().prepare("SELECT * FROM tasks WHERE status='pending' ORDER BY created_at ASC LIMIT ?").all(free);
          for (var row of rows) {
            if (running.size >= N) break;
            var ctl = { aborted: null };
            var entry = { abort: function (why) { ctl.aborted = why || "abort"; }, ctl: ctl };
            running.set(row.id, entry);
            entry.promise = executeTask(row, ctl).finally(function () { running.delete(row.id); kick(); });
          }
        }
      } catch (e) {
        console.error("queue loop err:", e.message || e);
      }
      await Promise.race([sleep(3000), waitKick()]);
    }
  };
  mainLoop();
}

async function executeTask(row, ctl) {
  var id = row.id;
  console.log("[queue] execute", id, row.type);
  try {
    var payload = JSON.parse(row.payload);
    await runTask(id, payload, ctl, function (status, extra) { setStatus(id, status, extra); });
    setStatus(id, "done", { progress: 100 });
    console.log("[queue] done", id);
  } catch (e) {
    var msg = String((e && e.message) || e || "unknown").slice(0, 500);
    if (msg.indexOf("cancelled") >= 0) { try { setStatus(id, "cancelled", {}); } catch (e9) {} return; }
    console.log("[queue] fail", id, msg.slice(0, 300));
    if (String(e.message || e).indexOf("cancelled") >= 0) return;
    try {
      setStatus(id, "failed", { error: msg });
    } catch (e2) {
      try { getDb().prepare("UPDATE tasks SET status='failed', error=?, updated_at=? WHERE id=?").run(msg, Date.now(), id); } catch (e3) {}
    }
  }
}
