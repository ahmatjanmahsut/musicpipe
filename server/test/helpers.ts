import fs from 'node:fs';
import path from 'node:path';
import { buildApp } from '../src/index.js';
import type { FastifyInstance } from 'fastify';

export interface TestEnv {
  app: FastifyInstance;
  base: string;
  dataDir: string;
  cookie: string;
}

export async function makeApp(env: Record<string, string> = {}): Promise<{ app: FastifyInstance; base: string; dataDir: string }> {
  const tmpBase = process.env.TEMP && fs.existsSync(process.env.TEMP) ? process.env.TEMP : path.resolve('.tmp-test');
  fs.mkdirSync(tmpBase, { recursive: true });
  const dataDir = fs.mkdtempSync(path.join(tmpBase, 'musicpipe-test-'));
  const app = await buildApp({ dataDir, port: 0, mockSubsonic: env.MOCK_SUBSONIC === '1' });
  await app.listen({ port: 0, host: '127.0.0.1' });
  const addr = app.server.address();
  const port = typeof addr === 'object' && addr ? addr.port : 0;
  return { app, base: `http://127.0.0.1:${port}`, dataDir };
}

export async function closeApp(app: FastifyInstance, dataDir: string): Promise<void> {
  await app.close();
  try {
    fs.rmSync(dataDir, { recursive: true, force: true });
  } catch {
    // Windows: lingering handles may block removal; leave temp dir rather than fail the test.
  }
}

export async function registerAndLogin(base: string, username: string, password: string): Promise<string> {
  const r = await fetch(`${base}/api/auth/register`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (r.status !== 201) throw new Error(`register failed: ${r.status}`);
  const l = await fetch(`${base}/api/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (l.status !== 200) throw new Error(`login failed: ${l.status}`);
  const setCookie = l.headers.get('set-cookie') || '';
  return setCookie.split(';')[0];
}

export function cookieHeader(cookie: string): Record<string, string> {
  return { cookie };
}

export function makeFakeApk(bytes = 1024): Blob {
  const buf = new Uint8Array(bytes);
  for (let i = 0; i < bytes; i++) buf[i] = i % 251;
  return new Blob([buf], { type: 'application/vnd.android.package-archive' });
}
