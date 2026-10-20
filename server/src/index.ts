import fs from 'node:fs';
import path from 'node:path';
import Fastify, { type FastifyInstance } from 'fastify';
import cookie from '@fastify/cookie';
import multipart from '@fastify/multipart';
import fastifyStatic from '@fastify/static';
import { loadConfig } from './config.js';
import { openDb } from './db/index.js';
import { requireSession, requireAdmin } from './services/auth.js';
import authRoutes from './routes/auth.js';
import userRoutes from './routes/users.js';
import updateRoutes from './routes/update.js';
import systemRoutes from './routes/system.js';
import lyricRoutes, { DirectoryLyricProvider, type LyricProvider } from './routes/lyrics.js';
import mockSubsonic from './mock/subsonic.js';

declare module 'fastify' {
  interface FastifyInstance {
    db: ReturnType<typeof openDb>;
    config: ReturnType<typeof loadConfig>;
    lyricProvider: LyricProvider;
    pkgVersion: string;
    requireSession: typeof requireSession;
    requireAdmin: typeof requireAdmin;
  }
}

export interface BuildAppOptions {
  port?: number;
  dataDir?: string;
  sessionTtlMs?: number;
  mockSubsonic?: boolean;
  portSub?: number;
}

export async function buildApp(opts: BuildAppOptions = {}): Promise<FastifyInstance> {
  const config = loadConfig({
    port: opts.port,
    dataDir: opts.dataDir,
    sessionTtlMs: opts.sessionTtlMs,
    mockSubsonic: opts.mockSubsonic,
    portSub: opts.portSub,
  });

  const app = Fastify({ logger: false });

  // 容忍无 Content-Type / 空 body 的 POST/PUT/PATCH/DELETE：
  // Retrofit `@POST logout()` 与 axios `post(url)` 都不带 Content-Type，
  // Fastify 默认会回 415。补上 json 头并让 json parser 接受空 body。
  app.addHook('onRequest', async (req) => {
    const m = req.method;
    if (!req.headers['content-type'] && (m === 'POST' || m === 'PUT' || m === 'PATCH' || m === 'DELETE')) {
      req.headers['content-type'] = 'application/json';
    }
  });
  app.addContentTypeParser('application/json', { parseAs: 'string' }, (_req, body, done) => {
    const text = (body as string | null) ?? '';
    if (!text.trim()) return done(null, undefined);
    try {
      done(null, JSON.parse(text) as unknown);
    } catch {
      const err = new Error('invalid json body') as Error & { statusCode?: number };
      err.statusCode = 400;
      done(err, undefined);
    }
  });
  // 其他未知 Content-Type（如某些客户端默认发 application/x-www-form-urlencoded 的空 POST）：
  // 一律容忍并按无 body 处理，避免 415。multipart 由 @fastify/multipart 自己的 parser 优先处理。
  app.addContentTypeParser('*', { parseAs: 'string' }, (_req, _body, done) => {
    done(null, undefined);
  });

  const pkg = JSON.parse(
    fs.readFileSync(path.resolve(__dirname, '..', 'package.json'), 'utf8')
  ) as { version: string };

  const db = openDb(config.dataDir);
  app.decorate('db', db);
  app.decorate('config', config);
  app.decorate('lyricProvider', new DirectoryLyricProvider(config.dataDir));
  app.decorate('pkgVersion', pkg.version);
  app.decorate('requireSession', requireSession);
  app.decorate('requireAdmin', requireAdmin);

  await app.register(cookie);
  await app.register(multipart, {
    limits: { fileSize: 200 * 1024 * 1024 },
  });

  await app.register(authRoutes);
  await app.register(userRoutes);
  await app.register(updateRoutes);
  await app.register(systemRoutes);
  await app.register(lyricRoutes);

  // web/dist 存在时静态托管 + SPA fallback
  const webDist = path.resolve(__dirname, '..', '..', 'web', 'dist');
  if (fs.existsSync(path.join(webDist, 'index.html'))) {
    await app.register(fastifyStatic, { root: webDist });
    app.setNotFoundHandler((request, reply) => {
      if (request.raw.url && request.raw.url.startsWith('/api/')) {
        return reply.code(404).send({ error: 'internal_error', message: 'Not Found' });
      }
      return reply.sendFile('index.html');
    });
  } else {
    app.setNotFoundHandler((_request, reply) => {
      reply.code(404).send({ error: 'internal_error', message: 'Not Found' });
    });
  }

  if (config.mockSubsonic) {
    const sub = Fastify({ logger: false });
    await sub.register(mockSubsonic);
    await sub.listen({ port: config.portSub, host: '0.0.0.0' });
    app.addHook('onClose', async () => { await sub.close(); });
  }

  return app;
}

export async function main(): Promise<void> {
  const app = await buildApp();
  await app.listen({ port: app.config.port, host: '0.0.0.0' });
  // eslint-disable-next-line no-console
  console.log(`musicpipe-appservice listening on :${app.config.port}`);
}

// 直接执行时启动（tsx / node dist/index.js）
if (process.argv[1] && (process.argv[1].endsWith('index.ts') || process.argv[1].endsWith('index.js')) && process.env.NODE_ENV !== 'test') {
  main().catch((err) => {
    // eslint-disable-next-line no-console
    console.error(err);
    process.exit(1);
  });
}
