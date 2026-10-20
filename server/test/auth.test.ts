import { test, describe, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { makeApp, closeApp, registerAndLogin, cookieHeader } from './helpers.js';

let ctx: { app: any; base: string; dataDir: string };

before(async () => { ctx = await makeApp(); });
after(async () => { await closeApp(ctx.app, ctx.dataDir); });

describe('auth', () => {
  test('first registered user becomes admin', async () => {
    const r = await fetch(`${ctx.base}/api/auth/register`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ username: 'boss', password: 'secret1' }),
    });
    assert.equal(r.status, 201);
    const body = await r.json();
    assert.equal(body.role, 'admin');
    assert.equal(body.username, 'boss');
  });

  test('second user is plain user', async () => {
    const r = await fetch(`${ctx.base}/api/auth/register`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ username: 'alice', password: 'secret1' }),
    });
    assert.equal(r.status, 201);
    assert.equal((await r.json()).role, 'user');
  });

  test('duplicate username 409 username_taken', async () => {
    const r = await fetch(`${ctx.base}/api/auth/register`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ username: 'boss', password: 'secret1' }),
    });
    assert.equal(r.status, 409);
    assert.equal((await r.json()).error, 'username_taken');
  });

  test('invalid username/password 400 invalid_params', async () => {
    for (const body of [{ username: 'ab', password: 'secret1' }, { username: 'ok_name', password: '12345' }]) {
      const r = await fetch(`${ctx.base}/api/auth/register`, {
        method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body),
      });
      assert.equal(r.status, 400);
      assert.equal((await r.json()).error, 'invalid_params');
    }
  });

  test('login ok returns cookie + user', async () => {
    const r = await fetch(`${ctx.base}/api/auth/login`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ username: 'boss', password: 'secret1' }),
    });
    assert.equal(r.status, 200);
    const sc = r.headers.get('set-cookie') || '';
    assert.ok(sc.includes('mp_session='));
    assert.ok(sc.includes('HttpOnly'));
    assert.equal((await r.json()).role, 'admin');
  });

  test('login unknown user 404 user_not_found', async () => {
    const r = await fetch(`${ctx.base}/api/auth/login`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ username: 'nobody', password: 'secret1' }),
    });
    assert.equal(r.status, 404);
    assert.equal((await r.json()).error, 'user_not_found');
  });

  test('login wrong password 401 wrong_password', async () => {
    const r = await fetch(`${ctx.base}/api/auth/login`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ username: 'boss', password: 'wrong!!' }),
    });
    assert.equal(r.status, 401);
    assert.equal((await r.json()).error, 'wrong_password');
  });

  test('protected endpoint without session 401 unauthorized', async () => {
    const r = await fetch(`${ctx.base}/api/auth/me`);
    assert.equal(r.status, 401);
    assert.equal((await r.json()).error, 'unauthorized');
  });

  test('me with session', async () => {
    const cookie = await registerAndLogin(ctx.base, 'me_user', 'secret1');
    const r = await fetch(`${ctx.base}/api/auth/me`, { headers: cookieHeader(cookie) });
    assert.equal(r.status, 200);
    assert.equal((await r.json()).username, 'me_user');
  });

  test('logout clears session', async () => {
    const cookie = await registerAndLogin(ctx.base, 'logout_user', 'secret1');
    const r = await fetch(`${ctx.base}/api/auth/logout`, { method: 'POST', headers: cookieHeader(cookie) });
    assert.equal(r.status, 204);
    const r2 = await fetch(`${ctx.base}/api/auth/me`, { headers: cookieHeader(cookie) });
    assert.equal(r2.status, 401);
  });
});
