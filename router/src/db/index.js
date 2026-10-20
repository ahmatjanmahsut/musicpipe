import { DatabaseSync } from "node:sqlite";
import fs from "node:fs";
import path from "node:path";

let db;

const MIGRATION_1 = [
  "CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT);",
  "CREATE TABLE sources(id TEXT PRIMARY KEY, name TEXT, file TEXT, enabled INTEGER DEFAULT 1, platforms TEXT DEFAULT '[]', loaded_ok INTEGER DEFAULT 0, last_error TEXT DEFAULT '', created_at INTEGER);",
  "CREATE TABLE tasks(id TEXT PRIMARY KEY, type TEXT, status TEXT, payload TEXT, progress INTEGER DEFAULT 0, speed INTEGER DEFAULT 0, message TEXT DEFAULT '', error TEXT DEFAULT '', retry INTEGER DEFAULT 0, track_id TEXT DEFAULT '', created_at INTEGER, updated_at INTEGER, finished_at INTEGER);",
  "CREATE INDEX idx_tasks_status ON tasks(status);",
  "CREATE TABLE tracks(id TEXT PRIMARY KEY, title TEXT, artist TEXT, album TEXT, platform TEXT, song_id TEXT, quality TEXT, file_path TEXT, remote_path TEXT, file_size INTEGER DEFAULT 0, status TEXT, source_id TEXT, task_id TEXT, duration INTEGER DEFAULT 0, extra TEXT DEFAULT '{}', created_at INTEGER, updated_at INTEGER);",
  "CREATE TABLE sessions(token TEXT PRIMARY KEY, created_at INTEGER, expires_at INTEGER);",
  "CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT, detail TEXT, created_at INTEGER);"
].join("\n");

export function initDb(dataDir) {
  fs.mkdirSync(dataDir, { recursive: true });
  db = new DatabaseSync(path.join(dataDir, "musicpipe.db"));
  db.exec("PRAGMA journal_mode=WAL;");
  db.exec("PRAGMA synchronous=FULL;");
  db.exec("CREATE TABLE IF NOT EXISTS _migrations(v INTEGER PRIMARY KEY)");
  const done = new Set(db.prepare("SELECT v FROM _migrations").all().map(function (r) { return r.v; }));
  if (!done.has(1)) {
    db.exec(MIGRATION_1);
    db.prepare("INSERT INTO _migrations(v) VALUES(?)").run(1);
  }
  if (!done.has(2)) {
    db.exec([
      "CREATE TABLE sharelinks(id TEXT PRIMARY KEY, url TEXT, platform TEXT, external_id TEXT, title TEXT, share_user TEXT, quality TEXT DEFAULT 'flac', status TEXT DEFAULT 'active', song_count INTEGER DEFAULT 0, added_at INTEGER, last_sync_at INTEGER DEFAULT 0, last_result TEXT DEFAULT '', fail_count INTEGER DEFAULT 0);",
      "CREATE TABLE sharelink_items(link_id TEXT, ext_key TEXT, title TEXT, artist TEXT, no INTEGER, task_id TEXT DEFAULT '', track_id TEXT DEFAULT '', state TEXT DEFAULT 'queued', added_at INTEGER, PRIMARY KEY(link_id, ext_key));"
    ].join("\n"));
    db.prepare("INSERT INTO _migrations(v) VALUES(?)").run(2);
  }
  if (!done.has(3)) {
    try { db.exec("ALTER TABLE sharelinks ADD COLUMN user_id TEXT DEFAULT ''"); } catch (e) {}
    try { db.exec("ALTER TABLE sharelink_items ADD COLUMN song_json TEXT DEFAULT ''"); } catch (e) {}
    try { db.exec("ALTER TABLE sharelink_items ADD COLUMN in_lib INTEGER DEFAULT 0"); } catch (e) {}
    db.prepare("INSERT INTO _migrations(v) VALUES(?)").run(3);
  }
  if (!done.has(4)) {
    // v4: App 多用户账号(角色) + APK 发布/更新服务（自 MusicPipe App 后端合并）
    db.exec([
      "CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'user', created_at INTEGER NOT NULL);",
      "CREATE TABLE app_releases(id INTEGER PRIMARY KEY AUTOINCREMENT, version_name TEXT NOT NULL, version_code INTEGER NOT NULL UNIQUE, md5 TEXT NOT NULL, size INTEGER NOT NULL DEFAULT 0, changelog TEXT DEFAULT '', file_path TEXT NOT NULL, created_at INTEGER NOT NULL);"
    ].join("\n"));
    try { db.exec("ALTER TABLE sessions ADD COLUMN user_id TEXT DEFAULT ''"); } catch (e) {}
    db.prepare("INSERT INTO _migrations(v) VALUES(?)").run(4);
  }
  if (!done.has(5)) {
    // v5: 账号体系统一 —— 控制台登录改用 users 表(用户名+密码, 仅 admin)；
    // 废止单密码 console_password，并使旧控制台会话(user_id='')全部失效
    try { db.exec("DELETE FROM sessions WHERE user_id IS NULL OR user_id=''"); } catch (e) {}
    try { db.exec("DELETE FROM settings WHERE key='console_password'"); } catch (e) {}
    db.prepare("INSERT INTO _migrations(v) VALUES(?)").run(5);
  }
  return db;
}

export function getDb() { return db; }

export function getSetting(key, dflt) {
  if (dflt === undefined) dflt = null;
  var r = db.prepare("SELECT value FROM settings WHERE key=?").get(key);
  if (!r) return dflt;
  try { return JSON.parse(r.value); } catch (e) { return r.value; }
}

export function setSetting(key, value) {
  db.prepare("INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value").run(key, JSON.stringify(value));
}

export function allSettings() {
  var out = {};
  for (var r of db.prepare("SELECT key,value FROM settings").all()) {
    try { out[r.key] = JSON.parse(r.value); } catch (e) { out[r.key] = r.value; }
  }
  return out;
}

export function logEvent(kind, detail) {
  db.prepare("INSERT INTO events(kind,detail,created_at) VALUES(?,?,?)").run(kind, typeof detail === "string" ? detail : JSON.stringify(detail), Date.now());
  db.prepare("DELETE FROM events WHERE id < (SELECT MAX(id) - 1000 FROM events)").run();
}
