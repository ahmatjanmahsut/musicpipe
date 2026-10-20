const base = 'http://127.0.0.1:7800';
const login = async (u, p) => {
  const r = await fetch(base + '/api/auth/login', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ username: u, password: p }) });
  const sc = r.headers.getSetCookie ? r.headers.getSetCookie() : [];
  return sc.map(c => c.split(';')[0]).join('; ');
};
let pass = 0, fail = 0;
const Check = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n + ' ' + (x || '')); } else { fail++; console.log('FAIL ' + n + ' ' + (x || '')); } };
(async () => {
  const ck = await login('boss', 'secret1');
  // 1) 命中: mock 曲库 song-1 晴天/周杰伦
  const r1 = await fetch(base + '/api/lyrics/search?title=' + encodeURIComponent('晴天') + '&artist=' + encodeURIComponent('周杰伦'), { headers: { cookie: ck } });
  const b1 = await r1.json().catch(() => ({}));
  const lrc = JSON.stringify(b1);
  Check('lyrics-hit-200', r1.status === 200 && lrc.length > 50, 'status=' + r1.status + ' len=' + lrc.length + ' head=' + lrc.slice(0, 120));
  // 2) 未命中: 优雅 404 lyric_not_found (App 降级提示不影响播放)
  const r2 = await fetch(base + '/api/lyrics/search?title=' + encodeURIComponent('不存在的歌') + '&artist=x', { headers: { cookie: ck } });
  const b2 = await r2.json().catch(() => ({}));
  Check('lyrics-miss-404', r2.status === 404 && b2.error === 'lyric_not_found', 'status=' + r2.status + ' ' + JSON.stringify(b2));
  // 3) 无会话 -> 401
  const r3 = await fetch(base + '/api/lyrics/search?title=' + encodeURIComponent('晴天'));
  Check('lyrics-401-nosession', r3.status === 401, 'status=' + r3.status);
  // 4) subsonic 搜索代理 (App 搜索页走的路径若经 server) — search2 直连 mock
  const r4 = await fetch(base + '/api/subsonic/rest/search3?query=' + encodeURIComponent('晴天'), { headers: { cookie: ck } });
  console.log('subsonic-proxy status:', r4.status, (await r4.text()).slice(0, 150));
  console.log('RESULT: pass=' + pass + ' fail=' + fail);
})().catch(e => { console.log('FATAL', e.message); process.exit(1); });
