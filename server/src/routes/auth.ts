import type { FastifyInstance } from 'fastify';
import {
  hashPassword, makeSalt, verifyPassword, createSession,
  setSessionCookie, clearSessionCookie, destroySession, getToken,
} from '../services/auth.js';

const USERNAME_RE = /^[A-Za-z0-9_\u4e00-\u9fa5]{3,32}$/;

export default async function authRoutes(app: FastifyInstance): Promise<void> {
  app.post('/api/auth/register', async (request, reply) => {
    const body = (request.body ?? {}) as { username?: unknown; password?: unknown };
    const username = typeof body.username === 'string' ? body.username : '';
    const password = typeof body.password === 'string' ? body.password : '';
    if (!USERNAME_RE.test(username) || password.length < 6) {
      return reply.code(400).send({ error: 'invalid_params', message: '用户名 3-32 位（字母/数字/下划线/中文），密码 >=6 位' });
    }
    const exists = app.db.prepare('SELECT id FROM users WHERE username = ?').get(username);
    if (exists) return reply.code(409).send({ error: 'username_taken', message: '用户名已存在' });
    const count = app.db.prepare('SELECT COUNT(*) AS n FROM users').get() as { n: number };
    const role = count.n === 0 ? 'admin' : 'user';
    const salt = makeSalt();
    const info = app.db
      .prepare('INSERT INTO users(username, password_hash, salt, role, created_at) VALUES(?,?,?,?,?)')
      .run(username, hashPassword(password, salt), salt, role, Date.now());
    return reply.code(201).send({ id: Number(info.lastInsertRowid), username, role });
  });

  app.post('/api/auth/login', async (request, reply) => {
    const body = (request.body ?? {}) as { username?: unknown; password?: unknown };
    const username = typeof body.username === 'string' ? body.username : '';
    const password = typeof body.password === 'string' ? body.password : '';
    const user = app.db.prepare('SELECT * FROM users WHERE username = ?').get(username) as
      | { id: number; username: string; password_hash: string; salt: string; role: 'admin' | 'user' }
      | undefined;
    if (!user) return reply.code(404).send({ error: 'user_not_found', message: '用户不存在' });
    if (!verifyPassword(password, user.salt, user.password_hash)) {
      return reply.code(401).send({ error: 'wrong_password', message: '密码错误' });
    }
    const token = createSession(app.db, user.id, app.config.sessionTtlMs);
    setSessionCookie(reply, token, app.config.sessionTtlMs);
    return { id: user.id, username: user.username, role: user.role };
  });

  app.post('/api/auth/logout', { preHandler: app.requireSession }, async (request, reply) => {
    destroySession(app.db, getToken(request));
    clearSessionCookie(reply);
    return reply.code(204).send();
  });

  app.get('/api/auth/me', { preHandler: app.requireSession }, async (request) => {
    return request.sessionUser!;
  });
}
