import { test, describe, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { makeApp, closeApp, registerAndLogin, cookieHeader } from './helpers.js';

let ctx: { app: any; base: string; dataDir: string };
let admin: string, user: string;

before(async () => {
  ctx = await makeApp();
  admin = await registerAndLogin(ctx.base, 'root_admin', 'secret1');
  user = await registerAndLogin(ctx.base, 'plain_user', 'secret1');
});
after(async () => { await closeApp(ctx.app, ctx.dataDir); });

describe('users', () => {
  test('user accessing user list 403 forbidden', async () => {
    const r = await fetch(`${ctx.base}/api/users`, { headers: cookieHeader(user) });
    assert.equal(r.status, 403);
    assert.equal((await r.json()).error, 'forbidden');
  });

  test('admin list users ascending with total', async () => {
    const r = await fetch(`${ctx.base}/api/users?page=1&limit=20`, { headers: cookieHeader(admin) });
    assert.equal(r.status, 200);
    const body = await r.json();
    assert.ok(body.total >= 2);
    const ids = body.items.map((u: any) => u.id);
    assert.deepEqual(ids, [...ids].sort((a: number, b: number) => a - b));
    assert.ok('created_at' in body.items[0]);
  });

  test('admin create user 201; duplicate 409', async () => {
    const r = await fetch(`${ctx.base}/api/users`, {
      method: 'POST', headers: { ...cookieHeader(admin), 'content-type': 'application/json' },
      body: JSON.stringify({ username: 'created1', password: 'secret1', role: 'user' }),
    });
    assert.equal(r.status, 201);
    const created = await r.json();
    assert.equal(created.role, 'user');
    const r2 = await fetch(`${ctx.base}/api/users`, {
      method: 'POST', headers: { ...cookieHeader(admin), 'content-type': 'application/json' },
      body: JSON.stringify({ username: 'created1', password: 'secret1', role: 'user' }),
    });
    assert.equal(r2.status, 409);
    assert.equal((await r2.json()).error, 'username_taken');
  });

  test('PUT change password + role; 404 unknown', async () => {
    const list = await (await fetch(`${ctx.base}/api/users`, { headers: cookieHeader(admin) })).json();
    const target = list.items.find((u: any) => u.username === 'created1');
    const r = await fetch(`${ctx.base}/api/users/${target.id}`, {
      method: 'PUT', headers: { ...cookieHeader(admin), 'content-type': 'application/json' },
      body: JSON.stringify({ password: 'newpass1' }),
    });
    assert.equal(r.status, 200);
    assert.equal((await r.json()).role, 'user');
    const r404 = await fetch(`${ctx.base}/api/users/99999`, {
      method: 'PUT', headers: { ...cookieHeader(admin), 'content-type': 'application/json' },
      body: JSON.stringify({ role: 'user' }),
    });
    assert.equal(r404.status, 404);
    assert.equal((await r404.json()).error, 'user_not_found');
  });

  test('PUT demote last admin 409 last_admin', async () => {
    const list = await (await fetch(`${ctx.base}/api/users`, { headers: cookieHeader(admin) })).json();
    const root = list.items.find((u: any) => u.username === 'root_admin');
    const r = await fetch(`${ctx.base}/api/users/${root.id}`, {
      method: 'PUT', headers: { ...cookieHeader(admin), 'content-type': 'application/json' },
      body: JSON.stringify({ role: 'user' }),
    });
    assert.equal(r.status, 409);
    assert.equal((await r.json()).error, 'last_admin');
  });

  test('DELETE self 400 cannot_delete_self', async () => {
    const list = await (await fetch(`${ctx.base}/api/users`, { headers: cookieHeader(admin) })).json();
    const root = list.items.find((u: any) => u.username === 'root_admin');
    const r = await fetch(`${ctx.base}/api/users/${root.id}`, { method: 'DELETE', headers: cookieHeader(admin) });
    assert.equal(r.status, 400);
    assert.equal((await r.json()).error, 'cannot_delete_self');
  });

  test('DELETE last admin 409 last_admin; DELETE other ok 204; 404 unknown', async () => {
    // 找一个非 admin 删除：先建一个
    const created = await (await fetch(`${ctx.base}/api/users`, {
      method: 'POST', headers: { ...cookieHeader(admin), 'content-type': 'application/json' },
      body: JSON.stringify({ username: 'todelete', password: 'secret1', role: 'user' }),
    })).json();
    const r = await fetch(`${ctx.base}/api/users/${created.id}`, { method: 'DELETE', headers: cookieHeader(admin) });
    assert.equal(r.status, 204);
    const r404 = await fetch(`${ctx.base}/api/users/${created.id}`, { method: 'DELETE', headers: cookieHeader(admin) });
    assert.equal(r404.status, 404);
    assert.equal((await r404.json()).error, 'user_not_found');
  });
});
