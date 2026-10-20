import fs from 'node:fs';
import path from 'node:path';
import type { FastifyInstance } from 'fastify';

export interface LyricSearchResult {
  title: string;
  artist: string;
  lrc: string;
  matchedBy: string;
}

export interface LyricProvider {
  /** 按平台 + songId 取歌词 */
  getBySongId(platform: string, songId: string): { platform: string; songId: string; lrc: string } | null;
  /** 按标题/艺人归一化模糊匹配 */
  search(title: string, artist?: string): LyricSearchResult | null;
}

export function normalize(s: string): string {
  return s
    .toLowerCase()
    .replace(/[\s\p{P}\p{S}]+/gu, '')
    .trim();
}

export class DirectoryLyricProvider implements LyricProvider {
  private index: { file: string; title: string; artist: string }[] = [];

  constructor(private dataDir: string) {
    this.rebuild();
  }

  rebuild(): void {
    this.index = [];
    const dir = path.join(this.dataDir, 'lyrics');
    if (!fs.existsSync(dir)) return;
    for (const f of fs.readdirSync(dir)) {
      if (!f.toLowerCase().endsWith('.lrc')) continue;
      const base = f.slice(0, -4);
      let title = base;
      let artist = '';
      const sep = base.indexOf(' - ');
      if (sep > 0) {
        title = base.slice(0, sep).trim();
        artist = base.slice(sep + 3).trim();
      }
      this.index.push({ file: f, title, artist });
    }
  }

  getBySongId(platform: string, songId: string): { platform: string; songId: string; lrc: string } | null {
    const p = path.join(this.dataDir, 'lyrics', 'by-id', `${platform}-${songId}.lrc`);
    if (!fs.existsSync(p)) return null;
    return { platform, songId, lrc: fs.readFileSync(p, 'utf8') };
  }

  search(title: string, artist?: string): LyricSearchResult | null {
    this.rebuild();
    const nTitle = normalize(title);
    const nArtist = artist ? normalize(artist) : '';
    if (!nTitle) return null;
    let best: { entry: { file: string; title: string; artist: string }; matchedBy: string } | null = null;
    for (const e of this.index) {
      const ne = normalize(e.title);
      const na = normalize(e.artist);
      if (ne === nTitle && (!nArtist || na === nArtist)) {
        best = { entry: e, matchedBy: nArtist && na === nArtist ? 'title+artist' : 'title' };
        break;
      }
      if (!best && ne.includes(nTitle) && (!nArtist || (na && na.includes(nArtist)))) {
        best = { entry: e, matchedBy: 'contains' };
      }
    }
    if (!best) return null;
    const lrc = fs.readFileSync(path.join(this.dataDir, 'lyrics', best.entry.file), 'utf8');
    return { title: best.entry.title, artist: best.entry.artist, lrc, matchedBy: best.matchedBy };
  }
}

export default async function lyricRoutes(app: FastifyInstance): Promise<void> {
  app.get('/api/lyrics', { preHandler: app.requireSession }, async (request, reply) => {
    const q = request.query as { platform?: string; songId?: string };
    if (!q.platform || !q.songId) {
      return reply.code(400).send({ error: 'invalid_params', message: 'platform 和 songId 必传' });
    }
    const hit = app.lyricProvider.getBySongId(q.platform, q.songId);
    if (!hit) return reply.code(404).send({ error: 'lyric_not_found', message: '未找到歌词' });
    return hit;
  });

  app.get('/api/lyrics/search', { preHandler: app.requireSession }, async (request, reply) => {
    const q = request.query as { title?: string; artist?: string };
    if (!q.title) return reply.code(400).send({ error: 'invalid_params', message: 'title 必传' });
    const hit = app.lyricProvider.search(q.title, q.artist);
    if (!hit) return reply.code(404).send({ error: 'lyric_not_found', message: '未匹配到歌词' });
    return hit;
  });
}
