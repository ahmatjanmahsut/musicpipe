import crypto from 'node:crypto';
import { test, describe, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { makeApp, closeApp, registerAndLogin } from './helpers.js';

let ctx: { app: any; base: string; dataDir: string };
let subBase: string;

function authParams(): string {
  const salt = crypto.randomBytes(4).toString('hex');
  const t = crypto.createHash('md5').update('admin123' + salt).digest('hex');
  return `u=admin&t=${t}&s=${salt}&v=1.16.1&c=MusicPipeApp&f=json`;
}

before(async () => {
  process.env.MOCK_SUBSONIC = '1';
  const subPort = String(4533 + (process.pid % 500));
  process.env.PORT_SUB = subPort;
  ctx = await makeApp({ MOCK_SUBSONIC: '1' } as Record<string, string>);
  await registerAndLogin(ctx.base, 'mock_user', 'secret1');
  subBase = `http://127.0.0.1:${subPort}`;
});
after(async () => { await closeApp(ctx.app, ctx.dataDir); });

async function sub(view: string, extra = ''): Promise<Response> {
  return fetch(`${subBase}/rest/${view}?${authParams()}${extra ? '&' + extra : ''}`);
}

describe('mock subsonic', () => {
  test('ping ok', async () => {
    const r = await sub('ping.view');
    assert.equal(r.status, 200);
    const b = await r.json();
    assert.equal(b['subsonic-response'].status, 'ok');
  });

  test('bad auth returns failed code 40', async () => {
    const r = await fetch(`${subBase}/rest/ping.view?u=admin&t=bad&s=xx&v=1.16.1&c=MusicPipeApp&f=json`);
    const b = await r.json();
    assert.equal(b['subsonic-response'].status, 'failed');
    assert.equal(b['subsonic-response'].error.code, 40);
  });

  test('getRandomSongs returns 4 songs', async () => {
    const b = await (await sub('getRandomSongs.view', 'size=30')).json();
    const songs = b['subsonic-response'].randomSongs.song;
    assert.equal(songs.length, 4);
    assert.ok(songs[0].coverArt);
  });

  test('search3 substring match', async () => {
    const b = await (await sub('search3.view', 'query=' + encodeURIComponent('周杰伦') + '&songCount=30&albumCount=10&artistCount=0')).json();
    const songs = b['subsonic-response'].searchResult3.song;
    assert.ok(songs.length >= 2);
    for (const s of songs) assert.ok(s.artist.includes('周杰伦'));
  });

  test('stream returns 3s WAV bytes', async () => {
    const r = await sub('stream.view', 'id=song-1');
    assert.equal(r.status, 200);
    const buf = Buffer.from(await r.arrayBuffer());
    assert.ok(buf.length > 44);
    assert.equal(buf.toString('ascii', 0, 4), 'RIFF');
    assert.equal(buf.toString('ascii', 8, 12), 'WAVE');
    const dataBytes = buf.readUInt32LE(40);
    const seconds = dataBytes / (44100 * 2);
    assert.ok(Math.abs(seconds - 3) < 0.01, `expected 3s, got ${seconds}s`);
  });

  test('getLyricsBySongId song-1 has lyrics, song-3 empty', async () => {
    const b1 = await (await sub('getLyricsBySongId.view', 'songId=song-1')).json();
    const lines1 = b1['subsonic-response'].lyricsList.structuredLyrics[0].line;
    assert.ok(lines1.length > 0);
    const b3 = await (await sub('getLyricsBySongId.view', 'songId=song-3')).json();
    assert.equal(b3['subsonic-response'].lyricsList.structuredLyrics.length, 0);
  });

  test('getLyrics fallback', async () => {
    const b = await (await sub('getLyrics.view', 'artist=' + encodeURIComponent('周杰伦') + '&title=' + encodeURIComponent('稻香'))).json();
    assert.ok(b['subsonic-response'].lyrics.value.includes('稻香'));
  });

  test('getCoverArt returns PNG', async () => {
    const r = await sub('getCoverArt.view', 'id=art-1');
    assert.equal(r.headers.get('content-type'), 'image/png');
    const buf = Buffer.from(await r.arrayBuffer());
    assert.ok(buf.length > 8);
    assert.deepEqual([...buf.slice(0, 8)], [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  });

  test('scrobble / star / unstar / getStarred2', async () => {
    assert.equal((await (await sub('scrobble.view', 'id=song-1')).json())['subsonic-response'].status, 'ok');
    assert.equal((await (await sub('star.view', 'id=song-1')).json())['subsonic-response'].status, 'ok');
    const b = await (await sub('getStarred2.view')).json();
    assert.equal(b['subsonic-response'].starred2.song.length, 1);
    await sub('unstar.view', 'id=song-1');
    const b2 = await (await sub('getStarred2.view')).json();
    assert.equal(b2['subsonic-response'].starred2.song.length, 0);
  });
});
