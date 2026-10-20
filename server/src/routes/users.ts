import type { FastifyInstance } from 'fastify';
import { hashPassword, makeSalt } from '../services/auth.js';

interface UserRow { id: number; username: string; role: 'admin' | 'user'; created_at: number; salt?: string; password_hash?: string }

function publicUser(u: UserRow) {
  return { id: u.id, username: u.username, role: u.role, ...(u.created_at !== undefined ? { created_at: u.created_at } : {}) };
}

export default async function userRoutes(app: FastifyInstance): Promise<void> {
  app.get('/api/users', { preHandler: app.requireAdmin }, async (request) => {
    const q = request.query as { page?: string; limit?: string };
    const page = Math.max(1, Number(q.page || 1) || 1);
    const limit = Math.min(100, Math.max(1, Number(q.limit || 20) || 20));
    const total = (app.db.prepare('SELECT COUNT(*) AS n FROM users').get() as { n: number }).n;
    const items = app.db
      .prepare('SELECT id, username, role, created_at FROM users ORDER BY id ASC LIMIT ? OFFSET ?')
      .all(limit, (page - 1) * limit);
    return { items, total };
  });

  app.post('/api/users', { preHandler: app.requireAdmin }, async (request, reply) => {
    const body = (request.body ?? {}) as { username?: unknown; password?: unknown; role?: unknown };
    const username = typeof body.username === 'string' ? body.username : '';
    const password = typeof body.password === 'string' ? body.password : '';
    const role = body.role === 'admin' ? 'admin' : 'user';
    if (!username || username.length < 3 || username.length > 32 || password.length < 6 || (body.role !== undefined && body.role !== 'admin' && body.role !== 'user')) {
      return reply.code(400).send({ error: 'invalid_params', message: '用户名/密码/角色不合法' });
    }
    const exists = app.db.prepare('SELECT id FROM users WHERE username = ?').get(username);
    if (exists) return reply.code(409).send({ error: 'username_taken', message: '用户名已存在' });
    const salt = makeSalt();
    const info = app.db
      .prepare('INSERT INTO users(username, password_hash, salt, role, created_at) VALUES(?,?,?,?,?)')
      .run(username, hashPassword(password, salt), salt, role, Date.now());
    return reply.code(201).send({ id: Number(info.lastInsertRowid), username, role });
  });

  app.put('/api/users/:id', { preHandler: app.requireAdmin }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const uid = Number(id);
    const body = (request.body ?? {}) as { password?: unknown; role?: unknown };
    const hasPassword = typeof body.password === 'string' && body.password.length >= 6;
    const hasRole = body.role === 'admin' || body.role === 'user';
    if (!hasPassword && !hasRole) {
      return reply.code(400).send({ error: 'invalid_params', message: '至少提供 password 或 role' });
    }
    const user = app.db.prepare('SELECT id, username, role FROM users WHERE id = ?').get(uid) as UserRow | undefined;
    if (!user) return reply.code(404).send({ error: 'user_not_found', message: '用户不存在' });

    if (hasRole && user.role === 'admin' && body.role === 'user') {
      const admins = app.db.prepare("SELECT COUNT(*) AS n FROM users WHERE role = 'admin'").get() as { n: number };
      if (admins.n <= 1) return reply.code(409).send({ error: 'last_admin', message: '不能降级最后一名管理员' });
    }
    if (hasPassword) {
      const salt = makeSalt();
      app.db.prepare('UPDATE users SET password_hash = ?, salt = ? WHERE id = ?')
        .run(hashPassword(body.password as string, salt), salt, uid);
    }
    if (hasRole) {
      app.db.prepare('UPDATE users SET role = ? WHERE id = ?').run(body.role, uid);
    }
    const updated = app.db.prepare('SELECT id, username, role FROM users WHERE id = ?').get(uid) as UserRow;
    return { id: updated.id, username: updated.username, role: updated.role };
  });

  app.delete('/api/users/:id', { preHandler: app.requireAdmin }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const uid = Number(id);
    const user = app.db.prepare('SELECT id, role FROM users WHERE id = ?').get(uid) as { id: number; role: string } | undefined;
    if (!user) return reply.code(404).send({ error: 'user_not_found', message: '用户不存在' });
    if (request.sessionUser && request.sessionUser.id === uid) {
      return reply.code(400).send({ error: 'cannot_delete_self', message: '不能删除自己' });
    }
    if (user.role === 'admin') {
      const admins = app.db.prepare("SELECT COUNT(*) AS n FROM users WHERE role = 'admin'").get() as { n: number };
      if (admins.n <= 1) return reply.code(409).send({ error: 'last_admin', message: '不能删除最后一名管理员' });
    }
    app.db.prepare('DELETE FROM users WHERE id = ?').run(uid);
    return reply.code(204).send();
  });
}
