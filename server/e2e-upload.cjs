
const base = 'http://127.0.0.1:7800';
let pass = 0, fail = 0;
const Check = (n, c, x = '') => { c ? (pass++, console.log('PASS ' + n + ' ' + x)) : (fail++, console.log('FAIL ' + n + ' ' + x)); };
const login = async (u, p) => {
  const r = await fetch(base + '/api/auth/login', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ username: u, password: p }) });
  if (!r.ok) throw new Error('login ' + r.status);
  const sc = r.headers.getSetCookie ? r.headers.getSetCookie() : [];
  return sc.map(c => c.split(';')[0]).join('; ');
};
const md5 = b => require('crypto').createHash('md5').update(b).digest('hex');
(async () => {
  const ckA = await login('boss', 'secret1');
  // build fake apk 4096 bytes
  const bytes = Buffer.alloc(4096); for (let i = 0; i < 4096; i++) bytes[i] = i % 251;
  const exp = md5(bytes);
  // 1 upload vc999
  const fd = new FormData();
  fd.append('versionName', '9.9.9'); fd.append('versionCode', '999'); fd.append('changelog', 'e2e node upload');
  fd.append('file', new Blob([bytes], { type: 'application/vnd.android.package-archive' }), 'e2e-999.apk');
  let rid = 0;
  const up = await fetch(base + '/api/app/releases', { method: 'POST', headers: { cookie: ckA }, body: fd });
  const upb = await up.json().catch(() => ({}));
  Check('upload-release', up.ok && upb.versionCode === 999 && upb.md5 === exp, JSON.stringify(upb).slice(0, 160));
  rid = upb.id;
  // 2 latest = 999 (as normal user session)
  const reg = await fetch(base + '/api/auth/register', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ username: 'e2en' + Date.now() % 99999, password: 'demo123456' }) });
  const ckB = await login((await reg.json()).username, 'demo123456');
  const lat = await (await fetch(base + '/api/app/update/latest', { headers: { cookie: ckB } })).json();
  Check('latest-is-999', lat.versionCode === 999 && lat.downloadUrl.includes('versionCode=999'), JSON.stringify(lat).slice(0, 120));
  // 3 download + md5
  const dl = await fetch(base + lat.downloadUrl, { headers: { cookie: ckB } });
  const buf = Buffer.from(await dl.arrayBuffer());
  Check('download-999-md5', dl.ok && md5(buf) === exp && buf.length === 4096, 'len=' + buf.length);
  // 4 non-admin upload -> 403
  const fd2 = new FormData();
  fd2.append('versionName', '1.1.1'); fd2.append('versionCode', '111'); fd2.append('changelog', 'x');
  fd2.append('file', new Blob([Buffer.alloc(8)]), 'x.apk');
  const r403 = await fetch(base + '/api/app/releases', { method: 'POST', headers: { cookie: ckB }, body: fd2 });
  Check('user-403-upload', r403.status === 403, 'status=' + r403.status);
  // 5 duplicate vc -> 4xx
  const fd3 = new FormData();
  fd3.append('versionName', 'dup'); fd3.append('versionCode', '2'); fd3.append('changelog', 'x');
  fd3.append('file', new Blob([Buffer.alloc(8)]), 'dup.apk');
  const rdup = await fetch(base + '/api/app/releases', { method: 'POST', headers: { cookie: ckA }, body: fd3 });
  Check('dup-vc-rejected', rdup.status >= 400 && rdup.status < 500, 'status=' + rdup.status);
  // 6 bad versionCode (non-numeric) -> 4xx
  const fd4 = new FormData();
  fd4.append('versionName', 'bad'); fd4.append('versionCode', 'abc'); fd4.append('changelog', 'x');
  fd4.append('file', new Blob([Buffer.alloc(8)]), 'bad.apk');
  const rbad = await fetch(base + '/api/app/releases', { method: 'POST', headers: { cookie: ckA }, body: fd4 });
  Check('bad-vc-rejected', rbad.status >= 400 && rbad.status < 500, 'status=' + rbad.status);
  // 7 delete release -> latest back to 2
  const del = await fetch(base + '/api/app/releases/' + rid, { method: 'DELETE', headers: { cookie: ckA } });
  const lat2 = await (await fetch(base + '/api/app/update/latest', { headers: { cookie: ckB } })).json();
  Check('delete-release', del.ok && lat2.versionCode === 2, 'vc=' + lat2.versionCode);
  // 8 system status with session
  const st = await fetch(base + '/api/system/status', { headers: { cookie: ckA } });
  const stb = await st.json().catch(() => ({}));
  Check('system-status', st.ok && stb.ok === true, JSON.stringify(stb));
  console.log('RESULT: pass=' + pass + ' fail=' + fail);
})().catch(e => { console.log('FATAL ' + e.message); process.exit(1); });
