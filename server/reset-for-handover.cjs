// 交接清库: 删除全部测试用户/会话/发布与假 APK, 让用户的第一个注册成为 admin
const Database = require('better-sqlite3');
const fs = require('fs');
const path = require('path');
const db = new Database('/path/to/musicpipe/server/data/musicpipe.db');
const tables = db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all().map(r => r.name);
console.log('tables:', tables.join(','));
for (const t of tables) {
  if (t === 'schema_version' || t.startsWith('sqlite_')) continue;
  const n = db.prepare('SELECT COUNT(*) c FROM "' + t + '"').get().c;
  db.prepare('DELETE FROM "' + t + '"').run();
  console.log('cleared', t, '(' + n + ' rows)');
}
// 假 APK 文件
const apkDir = '/path/to/musicpipe/server/data/apk';
if (fs.existsSync(apkDir)) {
  for (const f of fs.readdirSync(apkDir)) { fs.rmSync(path.join(apkDir, f), { force: true }); console.log('removed apk file:', f); }
}
console.log('users now:', db.prepare('SELECT COUNT(*) c FROM users').get().c);
db.close();
console.log('RESET DONE');
