// 全链路 Subsonic E2E: 模拟 App SubsonicAuthInterceptor (t = md5(password + s))
const crypto = require('crypto');
const fs = require('fs');
const base = 'http://127.0.0.1:7800';
const sub = 'http://127.0.0.1:4533';
const PASSWORD = 'admin123';
let pass = 0, fail = 0;
const Check = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n + ' ' + (x || '')); } else { fail++; console.log('FAIL ' + n + ' ' + (x || '')); } };
const auth = () => { const s = crypto.randomBytes(8).toString('hex'); const t = crypto.createHash('md5').update(PASSWORD + s).digest('hex'); return 'u=admin&s=' + s + '&t=' + t + '&v=1.16.1&c=musicpipe&f=json'; };
const rest = (view, extra) => sub + '/rest/' + view + '.view?' + auth() + (extra ? '&' + extra : '');
const login = async (u, p) => {
  const r = await fetch(base + '/api/auth/login', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ username: u, password: p }) });
  const sc = r.headers.getSetCookie ? r.headers.getSetCookie() : [];
  return sc.map(c => c.split(';')[0]).join('; ');
};
(async () => {
  // --- seed lyrics library (server-side /api/lyrics/search source) ---
  fs.mkdirSync('server/data/lyrics/by-id', { recursive: true });
  const lrc1 = '[00:00.00]晴天 - 周杰伦\n[00:12.50]故事的小黄花\n[00:18.30]从出生那年就飘着\n[00:24.80]童年的荡秋千\n[00:31.20]随记忆一直晃到现在\n';
  const lrc2 = '[00:00.00]稻香 - 周杰伦\n[00:10.00]对这个世界如果你有太多的抱怨\n[00:16.40]跌倒了就不敢继续往前走\n[00:22.80]为什么人要这么的脆弱堕落\n';
  fs.writeFileSync('server/data/lyrics/晴天 - 周杰伦.lrc', lrc1);
  fs.writeFileSync('server/data/lyrics/稻香 - 周杰伦.lrc', lrc2);
  fs.writeFileSync('server/data/lyrics/by-id/subsonic-song-1.lrc', lrc1);
  console.log('seeded 3 lrc files');

  // --- mock subsonic views (App 播放链路) ---
  const ping = await (await fetch(rest('ping'))).json();
  Check('ping', ping['subsonic-response'].status === 'ok');

  const rnd = await (await fetch(rest('getRandomSongs', 'count=30'))).json();
  const songs = rnd['subsonic-response'].randomSongs.song;
  Check('getRandomSongs', songs.length === 4 && songs[0].id === 'song-1', 'n=' + songs.length);

  const s3 = await (await fetch(rest('search3', 'query=' + encodeURIComponent('晴天')))).json();
  const hit = s3['subsonic-response'].searchResult3.song;
  Check('search3', hit.length === 1 && hit[0].title === '晴天', JSON.stringify(hit[0]).slice(0, 80));

  const ly = await (await fetch(rest('getLyricsBySongId', 'songId=song-1'))).json();
  const sl = ly['subsonic-response'].lyricsList.structuredLyrics[0];
  Check('getLyricsBySongId', sl.synced === true && sl.line.length === 5 && sl.line[1].start === 12500, JSON.stringify(sl.line[1]));

  const ly2 = await (await fetch(rest('getLyrics', 'title=' + encodeURIComponent('晴天') + '&artist=' + encodeURIComponent('周杰伦')))).json();
  Check('getLyrics', String(ly2['subsonic-response'].lyrics.value).includes('故事的小黄花'));

  const st = await fetch(rest('stream', 'id=song-1'));
  const buf = Buffer.from(await st.arrayBuffer());
  Check('stream-wav', st.status === 200 && st.headers.get('content-type') === 'audio/wav' && buf.slice(0, 4).toString() === 'RIFF', 'len=' + buf.length);

  const cov = await fetch(rest('getCoverArt', 'id=art-1'));
  const cb = Buffer.from(await cov.arrayBuffer());
  Check('getCoverArt-png', cov.status === 200 && cb[0] === 0x89 && cb.slice(1, 4).toString() === 'PNG');

  await fetch(rest('star', 'id=song-2'));
  const st2 = await (await fetch(rest('getStarred2'))).json();
  const starred = st2['subsonic-response'].starred2.song || [];
  Check('star-getStarred2', starred.length === 1 && starred[0].id === 'song-2');
  await fetch(rest('unstar', 'id=song-2'));
  const st3 = await (await fetch(rest('getStarred2'))).json();
  Check('unstar', (st3['subsonic-response'].starred2.song || []).length === 0);

  const scr = await (await fetch(rest('scrobble', 'id=song-1'))).json();
  Check('scrobble', scr['subsonic-response'].status === 'ok');

  // 错误密码 -> error 40
  const s = crypto.randomBytes(8).toString('hex');
  const bad = sub + '/rest/ping.view?u=admin&s=' + s + '&t=' + crypto.createHash('md5').update('wrongpass' + s).digest('hex') + '&v=1.16.1&c=musicpipe&f=json';
  const badr = await (await fetch(bad)).json();
  Check('auth-fail-40', badr['subsonic-response'].status === 'failed' && badr['subsonic-response'].error.code === 40);

  // --- server 歌词 API (App LyricsRepository 路径) ---
  const ck = await login('boss', 'secret1');
  const a1 = await fetch(base + '/api/lyrics/search?title=' + encodeURIComponent('晴天') + '&artist=' + encodeURIComponent('周杰伦'), { headers: { cookie: ck } });
  const b1 = await a1.json();
  Check('api-lyrics-search-hit', a1.status === 200 && b1.lrc.includes('故事的小黄花') && b1.matchedBy === 'title+artist', 'matchedBy=' + b1.matchedBy);
  const a2 = await fetch(base + '/api/lyrics/search?title=' + encodeURIComponent('稻香'), { headers: { cookie: ck } });
  const b2 = await a2.json();
  Check('api-lyrics-search-title-only', a2.status === 200 && b2.matchedBy === 'title', 'matchedBy=' + b2.matchedBy);
  const a3 = await fetch(base + '/api/lyrics?platform=subsonic&songId=song-1', { headers: { cookie: ck } });
  const b3 = await a3.json();
  Check('api-lyrics-by-id', a3.status === 200 && b3.lrc.includes('晴天'), 'status=' + a3.status);
  const a4 = await fetch(base + '/api/lyrics/search?title=' + encodeURIComponent('没有这首歌'), { headers: { cookie: ck } });
  Check('api-lyrics-404-graceful', a4.status === 404);

  console.log('RESULT: pass=' + pass + ' fail=' + fail);
  process.exit(fail > 0 ? 1 : 0);
})().catch(e => { console.log('FATAL', e.message); process.exit(1); });
