import crypto from 'node:crypto';
import type { FastifyReply, FastifyRequest } from 'fastify';
import type { DB } from '../db/index.js';

export const COOKIE_NAME = 'mp_session';

export function hashPassword(password: string, salt: string): string {
  return crypto.scryptSync(password, salt, 64).toString('hex');
}

export function makeSalt(): string {
  return crypto.randomBytes(16).toString('hex');
}

export function verifyPassword(password: string, salt: string, hash: string): boolean {
  const computed = hashPassword(password, salt);
  const a = Buffer.from(computed, 'hex');
  const b = Buffer.from(hash, 'hex');
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

export function newSessionToken(): string {
  return crypto.randomBytes(32).toString('hex');
}

export function sha256Hex(s: string): string {
  return crypto.createHash('sha256').update(s).digest('hex');
}

export function createSession(db: DB, userId: number, ttlMs: number): string {
  const token = newSessionToken();
  db.prepare('INSERT INTO sessions(token_hash, user_id, expires_at) VALUES(?,?,?)')
    .run(sha256Hex(token), userId, Date.now() + ttlMs);
  return token;
}

export function setSessionCookie(reply: FastifyReply, token: string, ttlMs: number): void {
  const expires = new Date(Date.now() + ttlMs);
  reply.setCookie(COOKIE_NAME, token, {
    httpOnly: true,
    sameSite: 'lax',
    path: '/',
    expires,
  });
}

export function clearSessionCookie(reply: FastifyReply): void {
  reply.setCookie(COOKIE_NAME, '', { httpOnly: true, sameSite: 'lax', path: '/', maxAge: 0 });
}

export interface SessionUser {
  id: number;
  username: string;
  role: 'admin' | 'user';
}

declare module 'fastify' {
  interface FastifyRequest {
    sessionUser?: SessionUser;
  }
}

export function resolveSession(db: DB, token: string | undefined): SessionUser | null {
  if (!token) return null;
  const row = db
    .prepare(
      `SELECT users.id, users.username, users.role FROM sessions s JOIN users ON users.id = s.user_id
       WHERE s.token_hash = ? AND s.expires_at > ?`
    )
    .get(sha256Hex(token), Date.now()) as { id: number; username: string; role: 'admin' | 'user' } | undefined;
  if (!row) return null;
  return { id: row.id, username: row.username, role: row.role };
}

export function destroySession(db: DB, token: string | undefined): void {
  if (!token) return;
  db.prepare('DELETE FROM sessions WHERE token_hash = ?').run(sha256Hex(token));
}

export function getToken(request: FastifyRequest): string | undefined {
  const c = (request as unknown as { cookies?: Record<string, string> }).cookies;
  return c ? c[COOKIE_NAME] : undefined;
}

export async function requireSession(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const user = resolveSession(request.server.db, getToken(request));
  if (!user) {
    reply.code(401).send({ error: 'unauthorized', message: '需要有效会话' });
    return;
  }
  request.sessionUser = user;
}

export async function requireAdmin(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  await requireSession(request, reply);
  if (reply.sent) return;
  if (request.sessionUser?.role !== 'admin') {
    reply.code(403).send({ error: 'forbidden', message: '需要管理员权限' });
  }
}
