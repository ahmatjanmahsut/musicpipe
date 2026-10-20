const db = require('better-sqlite3')('server/data/musicpipe.db');
const r = db.prepare("DELETE FROM users WHERE username LIKE 'e2e%' OR username IN ('webtest1','uicheck1')").run();
console.log('deleted users:', r.changes);
const rel = db.prepare('DELETE FROM app_releases WHERE version_code=888').run();
console.log('deleted releases:', rel.changes);
console.log('users now:', db.prepare('SELECT id,username,role FROM users').all().map(u => u.id + ':' + u.username + ':' + u.role).join(', '));
console.log('releases now:', db.prepare('SELECT id,version_code,version_name FROM app_releases').all().map(x => x.id + ':vc' + x.version_code + ':' + x.version_name).join(', '));
