import crypto from 'node:crypto';
import { test, describe, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { makeApp, closeApp, registerAndLogin, cookieHeader, makeFakeApk } from './helpers.js';

let ctx: { app: any; base: string; dataDir: string };
let admin: string, user: string;

before(async () => {
  ctx = await makeApp();
  admin = await registerAndLogin(ctx.base, 'upd_admin', 'secret1');
  user = await registerAndLogin(ctx.base, 'upd_user', 'secret1');
});
after(async () => { await closeApp(ctx.app, ctx.dataDir); });

async function upload(cookie: string, fields: Record<string, string>, file: Blob, filename: string) {
  const form = new FormData();
  for (const [k, v] of Object.entries(fields)) form.append(k, v);
  form.append('file', file, filename);
  return fetch(`${ctx.base}/api/app/releases`, { method: 'POST', headers: cookieHeader(cookie), body: form as any });
}

describe('update', () => {
  test('latest with no releases 404 no_release', async () => {
    const r = await fetch(`${ctx.base}/api/app/update/latest`, { headers: cookieHeader(user) });
    assert.equal(r.status, 404);
    assert.equal((await r.json()).error, 'no_release');
  });

  test('user cannot upload 403', async () => {
    const r = await upload(user, { versionName: '1.0.0', versionCode: '1' }, makeFakeApk(), 'app.apk');
    assert.equal(r.status, 403);
    assert.equal((await r.json()).error, 'forbidden');
  });

  test('non-apk rejected 400 bad_apk', async () => {
    const r = await upload(admin, { versionName: '1.0.0', versionCode: '1' }, makeFakeApk(), 'app.zip');
    assert.equal(r.status, 400);
    assert.equal((await r.json()).error, 'bad_apk');
  });

  test('upload ok 201 with md5/size; duplicate versionCode 409', async () => {
    const blob = makeFakeApk(2048);
    const buf = Buffer.from(await blob.arrayBuffer());
    const md5 = crypto.createHash('md5').update(buf).digest('hex');
    const r = await upload(admin, { versionName: '1.0.0', versionCode: '1', changelog: 'first' }, blob, 'app.apk');
    assert.equal(r.status, 201);
    const body = await r.json();
    assert.equal(body.md5, md5);
    assert.equal(body.size, 2048);
    assert.equal(body.versionName, '1.0.0');
    assert.equal(body.versionCode, 1);
    assert.equal(body.downloadUrl, '/api/app/releases/download?versionCode=1');
    const r2 = await upload(admin, { versionName: '1.0.1', versionCode: '1' }, makeFakeApk(), 'app.apk');
    assert.equal(r2.status, 409);
    assert.equal((await r2.json()).error, 'duplicate_version_code');
  });

  test('latest returns newest versionCode with fields', async () => {
    await upload(admin, { versionName: '0.9.0', versionCode: '5' }, makeFakeApk(100), 'app.apk');
    const r = await fetch(`${ctx.base}/api/app/update/latest`, { headers: cookieHeader(user) });
    assert.equal(r.status, 200);
    const b = await r.json();
    assert.equal(b.versionCode, 5);
    assert.equal(b.versionName, '0.9.0');
    assert.ok(b.downloadUrl.includes('versionCode=5'));
  });

  test('releases list admin only, versionCode desc', async () => {
    const ru = await fetch(`${ctx.base}/api/app/releases`, { headers: cookieHeader(user) });
    assert.equal(ru.status, 403);
    const r = await fetch(`${ctx.base}/api/app/releases`, { headers: cookieHeader(admin) });
    assert.equal(r.status, 200);
    const b = await r.json();
    const vcs = b.items.map((x: any) => x.versionCode);
    assert.deepEqual(vcs, [...vcs].sort((a: number, c: number) => c - a));
  });

  test('download stream: bytes identical + X-Apk-Md5 + Content-Type; user allowed', async () => {
    const blob = makeFakeApk(5000);
    const buf = Buffer.from(await blob.arrayBuffer());
    const md5 = crypto.createHash('md5').update(buf).digest('hex');
    await upload(admin, { versionName: '2.0.0', versionCode: '10' }, blob, 'app.apk');
    const r = await fetch(`${ctx.base}/api/app/releases/download?versionCode=10`, { headers: cookieHeader(user) });
    assert.equal(r.status, 200);
    assert.equal(r.headers.get('x-apk-md5'), md5);
    assert.equal(r.headers.get('content-type'), 'application/vnd.android.package-archive');
    const got = Buffer.from(await r.arrayBuffer());
    assert.ok(got.equals(buf));
  });

  test('download unknown versionCode 404 release_not_found', async () => {
    const r = await fetch(`${ctx.base}/api/app/releases/download?versionCode=999`, { headers: cookieHeader(user) });
    assert.equal(r.status, 404);
    assert.equal((await r.json()).error, 'release_not_found');
  });

  test('DELETE release removes file, 404 unknown', async () => {
    const created = await (await upload(admin, { versionName: '3.0.0', versionCode: '20' }, makeFakeApk(300), 'app.apk')).json();
    const r = await fetch(`${ctx.base}/api/app/releases/${created.id}`, { method: 'DELETE', headers: cookieHeader(admin) });
    assert.equal(r.status, 204);
    const r2 = await fetch(`${ctx.base}/api/app/releases/${created.id}`, { method: 'DELETE', headers: cookieHeader(admin) });
    assert.equal(r2.status, 404);
    assert.equal((await r2.json()).error, 'release_not_found');
  });
});
