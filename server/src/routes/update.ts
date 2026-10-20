import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { pipeline } from 'node:stream/promises';
import type { FastifyInstance, FastifyReply } from 'fastify';
import type { DB } from '../db/index.js';

const MAX_APK_SIZE = 200 * 1024 * 1024;

interface ReleaseRow {
  id: number; version_name: string; version_code: number; apk_path: string;
  apk_size: number; md5: string; changelog: string; created_at: number;
}

function releaseDto(r: ReleaseRow) {
  return {
    id: r.id,
    versionName: r.version_name,
    versionCode: r.version_code,
    md5: r.md5,
    size: r.apk_size,
    changelog: r.changelog,
    downloadUrl: `/api/app/releases/download?versionCode=${r.version_code}`,
    ...(r.created_at !== undefined ? { createdAt: r.created_at, publishedAt: r.created_at } : {}),
  };
}

export default async function updateRoutes(app: FastifyInstance): Promise<void> {
  app.get('/api/app/update/latest', { preHandler: app.requireSession }, async (_req, reply) => {
    const latest = app.db.prepare('SELECT * FROM app_releases ORDER BY version_code DESC LIMIT 1').get() as ReleaseRow | undefined;
    if (!latest) return reply.code(404).send({ error: 'no_release', message: '尚无发布版本' });
    return releaseDto(latest);
  });

  app.post('/api/app/releases', { preHandler: app.requireAdmin }, async (request, reply) => {
    const part = await request.file({ limits: { fileSize: MAX_APK_SIZE } });
    if (!part) return reply.code(400).send({ error: 'bad_apk', message: '缺少文件字段' });

    const fields: Record<string, string> = {};
    for (const [k, v] of Object.entries(part.fields)) {
      const val = Array.isArray(v) ? v[0] : v;
      if (val && typeof val === 'object' && 'value' in (val as object) && !(val as { file?: unknown }).file) {
        fields[k] = String((val as { value: unknown }).value);
      }
    }
    const versionName = fields.versionName || '';
    const versionCode = Number(fields.versionCode);
    const changelog = fields.changelog ?? '';

    if (!versionName || !Number.isInteger(versionCode) || versionCode <= 0) {
      return reply.code(400).send({ error: 'invalid_params', message: 'versionName/versionCode 不合法' });
    }
    const filename = part.filename || '';
    if (!filename.toLowerCase().endsWith('.apk')) {
      return reply.code(400).send({ error: 'bad_apk', message: '仅接受 .apk 文件' });
    }

    const dup = app.db.prepare('SELECT id FROM app_releases WHERE version_code = ?').get(versionCode);
    if (dup) return reply.code(409).send({ error: 'duplicate_version_code', message: 'versionCode 已存在' });

    const dataDir = app.config.dataDir;
    const apkDir = path.join(dataDir, 'apk');
    fs.mkdirSync(apkDir, { recursive: true });
    const stored = `${versionCode}-${Date.now()}.apk`;
    const dest = path.join(apkDir, stored);

    const hash = crypto.createHash('md5');
    const out = fs.createWriteStream(dest);
    let size = 0;
    try {
      await pipeline(part.file, async function* (source) {
        for await (const chunk of source) {
          const buf = chunk as Buffer;
          size += buf.length;
          if (size > MAX_APK_SIZE) throw new Error('TOO_LARGE');
          hash.update(buf);
          yield buf;
        }
      }, out);
    } catch (err) {
      out.destroy();
      try { fs.unlinkSync(dest); } catch { /* ignore */ }
      if (err instanceof Error && err.message === 'TOO_LARGE') {
        return reply.code(400).send({ error: 'bad_apk', message: '文件超过 200MB 限制' });
      }
      throw err;
    }

    const md5 = hash.digest('hex');
    const info = app.db
      .prepare('INSERT INTO app_releases(version_name, version_code, apk_path, apk_size, md5, changelog, created_at) VALUES(?,?,?,?,?,?,?)')
      .run(versionName, versionCode, stored, size, md5, changelog, Date.now());
    return reply.code(201).send(releaseDto({
      id: Number(info.lastInsertRowid), version_name: versionName, version_code: versionCode,
      apk_path: stored, apk_size: size, md5, changelog, created_at: Date.now(),
    }));
  });

  app.get('/api/app/releases', { preHandler: app.requireAdmin }, async (request) => {
    const q = request.query as { page?: string; limit?: string };
    const page = Math.max(1, Number(q.page || 1) || 1);
    const limit = Math.min(100, Math.max(1, Number(q.limit || 20) || 20));
    const total = (app.db.prepare('SELECT COUNT(*) AS n FROM app_releases').get() as { n: number }).n;
    const rows = app.db
      .prepare('SELECT * FROM app_releases ORDER BY version_code DESC LIMIT ? OFFSET ?')
      .all(limit, (page - 1) * limit) as ReleaseRow[];
    return { items: rows.map(releaseDto), total };
  });

  app.get('/api/app/releases/download', { preHandler: app.requireSession }, async (request, reply) => {
    const q = request.query as { versionCode?: string };
    const vc = Number(q.versionCode);
    if (!Number.isInteger(vc)) return reply.code(404).send({ error: 'release_not_found', message: '发布不存在' });
    const row = app.db.prepare('SELECT * FROM app_releases WHERE version_code = ?').get(vc) as ReleaseRow | undefined;
    if (!row) return reply.code(404).send({ error: 'release_not_found', message: '发布不存在' });
    sendApk(app.db, app.config.dataDir, row, reply);
    return reply;
  });

  app.delete('/api/app/releases/:id', { preHandler: app.requireAdmin }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const row = app.db.prepare('SELECT * FROM app_releases WHERE id = ?').get(Number(id)) as ReleaseRow | undefined;
    if (!row) return reply.code(404).send({ error: 'release_not_found', message: '发布不存在' });
    const abs = path.join(app.config.dataDir, 'apk', row.apk_path);
    try { fs.unlinkSync(abs); } catch { /* ignore */ }
    app.db.prepare('DELETE FROM app_releases WHERE id = ?').run(row.id);
    return reply.code(204).send();
  });
}

export function sendApk(db: DB, dataDir: string, row: ReleaseRow, reply: FastifyReply): void {
  const abs = path.join(dataDir, 'apk', row.apk_path);
  if (!fs.existsSync(abs)) {
    reply.code(404).send({ error: 'release_not_found', message: 'APK 文件缺失' });
    return;
  }
  const stream = fs.createReadStream(abs);
  reply
    .code(200)
    .header('Content-Type', 'application/vnd.android.package-archive')
    .header('Content-Length', row.apk_size)
    .header('X-Apk-Md5', row.md5)
    .send(stream);
}
