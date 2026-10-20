import fs from 'node:fs';
import path from 'node:path';
import { test, describe, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { makeApp, closeApp, registerAndLogin, cookieHeader } from './helpers.js';

let ctx: { app: any; base: string; dataDir: string };
let cookie: string;

before(async () => {
  ctx = await makeApp();
  cookie = await registerAndLogin(ctx.base, 'lyr_user', 'secret1');
  const dir = path.join(ctx.dataDir, 'lyrics');
  fs.writeFileSync(path.join(dir, '晴天 - 周杰伦.lrc'), '[00:01.00]故事的小黄花\n[00:05.00]从出生那年就飘着');
  fs.writeFileSync(path.join(dir, '孤勇者.lrc'), '[00:01.00]都是勇敢的');
  fs.writeFileSync(path.join(ctx.dataDir, 'lyrics', 'by-id', 'subsonic-song-9.lrc'), '[00:01.00]by-id 歌词');
});
after(async () => { await closeApp(ctx.app, ctx.dataDir); });

describe('lyrics', () => {
  test('search hit by title+artist', async () => {
    const r = await fetch(`${ctx.base}/api/lyrics/search?title=${encodeURIComponent('晴 天！')}&artist=${encodeURIComponent('周杰伦')}`, { headers: cookieHeader(cookie) });
    assert.equal(r.status, 200);
    const b = await r.json();
    assert.equal(b.title, '晴天');
    assert.equal(b.artist, '周杰伦');
    assert.ok(b.lrc.includes('故事的小黄花'));
    assert.ok(b.matchedBy);
  });

  test('search hit by title only file', async () => {
    const r = await fetch(`${ctx.base}/api/lyrics/search?title=${encodeURIComponent('孤勇者')}`, { headers: cookieHeader(cookie) });
    assert.equal(r.status, 200);
    assert.ok((await r.json()).lrc.includes('都是勇敢的'));
  });

  test('search miss 404 lyric_not_found', async () => {
    const r = await fetch(`${ctx.base}/api/lyrics/search?title=${encodeURIComponent('不存在的歌')}`, { headers: cookieHeader(cookie) });
    assert.equal(r.status, 404);
    assert.equal((await r.json()).error, 'lyric_not_found');
  });

  test('search without title 400 invalid_params', async () => {
    const r = await fetch(`${ctx.base}/api/lyrics/search`, { headers: cookieHeader(cookie) });
    assert.equal(r.status, 400);
  });

  test('GET /api/lyrics by platform+songId hit & miss', async () => {
    const ok = await fetch(`${ctx.base}/api/lyrics?platform=subsonic&songId=song-9`, { headers: cookieHeader(cookie) });
    assert.equal(ok.status, 200);
    const b = await ok.json();
    assert.equal(b.platform, 'subsonic');
    assert.equal(b.songId, 'song-9');
    assert.ok(b.lrc.includes('by-id 歌词'));
    const miss = await fetch(`${ctx.base}/api/lyrics?platform=subsonic&songId=none`, { headers: cookieHeader(cookie) });
    assert.equal(miss.status, 404);
    assert.equal((await miss.json()).error, 'lyric_not_found');
  });
});
