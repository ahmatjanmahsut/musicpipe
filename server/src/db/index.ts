import fs from 'node:fs';
import path from 'node:path';
import Database from 'better-sqlite3';

export type DB = Database.Database;

function migrationPath(): string {
  const candidates = [
    path.resolve(__dirname, 'migrations', '001_init.sql'),
    path.resolve(process.cwd(), 'src', 'db', 'migrations', '001_init.sql'),
  ];
  for (const p of candidates) if (fs.existsSync(p)) return p;
  throw new Error('migration 001_init.sql not found');
}

export function openDb(dataDir: string): DB {
  fs.mkdirSync(dataDir, { recursive: true });
  fs.mkdirSync(path.join(dataDir, 'apk'), { recursive: true });
  fs.mkdirSync(path.join(dataDir, 'lyrics'), { recursive: true });
  fs.mkdirSync(path.join(dataDir, 'lyrics', 'by-id'), { recursive: true });

  const db = new Database(path.join(dataDir, 'musicpipe.db'));
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');
  db.exec(fs.readFileSync(migrationPath(), 'utf8'));
  return db;
}
