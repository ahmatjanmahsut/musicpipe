import type { FastifyInstance } from 'fastify';

export default async function systemRoutes(app: FastifyInstance): Promise<void> {
  app.get('/api/system/status', { preHandler: app.requireSession }, async () => {
    const releases = (app.db.prepare('SELECT COUNT(*) AS n FROM app_releases').get() as { n: number }).n;
    return {
      ok: true,
      version: app.pkgVersion,
      uptimeSec: Math.floor(process.uptime()),
      db: 'ok',
      releases,
    };
  });
}
