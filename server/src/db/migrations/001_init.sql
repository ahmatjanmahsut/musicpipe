CREATE TABLE IF NOT EXISTS users (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  username      TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  salt          TEXT NOT NULL,
  role          TEXT NOT NULL DEFAULT 'user' CHECK(role IN ('admin','user')),
  created_at    INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions (
  token_hash TEXT PRIMARY KEY,
  user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS app_releases (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  version_name TEXT NOT NULL,
  version_code INTEGER NOT NULL UNIQUE,
  apk_path     TEXT NOT NULL,
  apk_size     INTEGER NOT NULL,
  md5          TEXT NOT NULL,
  changelog    TEXT NOT NULL DEFAULT '',
  created_at   INTEGER NOT NULL
);
