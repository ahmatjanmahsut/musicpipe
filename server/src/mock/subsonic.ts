import crypto from 'node:crypto';
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';

interface Song {
  id: string; title: string; artist: string; album: string;
  duration: number; coverArt: string; suffix: string; bitRate: number; size: number;
  lyrics?: string;
}

const SONGS: Song[] = [
  {
    id: 'song-1', title: '晴天', artist: '周杰伦', album: '叶惠美',
    duration: 269, coverArt: 'art-1', suffix: 'mp3', bitRate: 320, size: 10800000,
    lyrics: `[00:00.00]晴天 - 周杰伦
[00:12.50]故事的小黄花
[00:18.30]从出生那年就飘着
[00:24.80]童年的荡秋千
[00:31.20]随记忆一直晃到现在`,
  },
  {
    id: 'song-2', title: '稻香', artist: '周杰伦', album: '魔杰座',
    duration: 223, coverArt: 'art-2', suffix: 'mp3', bitRate: 320, size: 8950000,
    lyrics: `[00:00.00]稻香 - 周杰伦
[00:10.00]对这个世界如果你有太多的抱怨
[00:16.40]跌倒了就不敢继续往前走
[00:22.80]为什么人要这么的脆弱堕落`,
  },
  {
    id: 'song-3', title: '孤勇者', artist: '陈奕迅', album: '孤勇者',
    duration: 262, coverArt: 'art-3', suffix: 'mp3', bitRate: 320, size: 10500000,
  },
  {
    id: 'song-4', title: '起风了', artist: '买辣椒也用券', album: '起风了',
    duration: 325, coverArt: 'art-4', suffix: 'mp3', bitRate: 320, size: 13000000,
  },
];

const starred = new Set<string>();

function md5Hex(s: string): string { return crypto.createHash('md5').update(s).digest('hex'); }

function checkAuth(query: Record<string, unknown>): boolean {
  const u = query.u, t = query.t, s = query.s;
  if (typeof u !== 'string' || typeof t !== 'string' || typeof s !== 'string') return false;
  return md5Hex('admin123' + s) === t;
}

function envelope(reply: FastifyReply, version: string, inner: Record<string, unknown>): void {
  reply.header('Content-Type', 'application/json; charset=utf-8').send({
    'subsonic-response': { status: 'ok', version, ...inner },
  });
}

function fail(reply: FastifyReply, version: string, code: number, message: string): void {
  reply.header('Content-Type', 'application/json; charset=utf-8').send({
    'subsonic-response': { status: 'failed', version, error: { code, message } },
  });
}

function childOf(s: Song) {
  return {
    id: s.id, title: s.title, artist: s.artist, album: s.album,
    duration: s.duration, coverArt: s.coverArt, suffix: s.suffix,
    bitRate: s.bitRate, size: s.size, isDir: false,
    parent: 'album-' + s.album, type: 'music',
  };
}

function makeWav(seconds = 3, sampleRate = 44100): Buffer {
  const numSamples = seconds * sampleRate;
  const dataSize = numSamples * 2;
  const buf = Buffer.alloc(44 + dataSize);
  buf.write('RIFF', 0);
  buf.writeUInt32LE(36 + dataSize, 4);
  buf.write('WAVE', 8);
  buf.write('fmt ', 12);
  buf.writeUInt32LE(16, 16);
  buf.writeUInt16LE(1, 20); // PCM
  buf.writeUInt16LE(1, 22); // mono
  buf.writeUInt32LE(sampleRate, 24);
  buf.writeUInt32LE(sampleRate * 2, 28); // byte rate
  buf.writeUInt16LE(2, 32); // block align
  buf.writeUInt16LE(16, 34);
  buf.write('data', 36);
  buf.writeUInt32LE(dataSize, 40);
  for (let i = 0; i < numSamples; i++) {
    const t = i / sampleRate;
    const v = Math.round(Math.sin(2 * Math.PI * 440 * t) * 12000);
    buf.writeInt16LE(v, 44 + i * 2);
  }
  return buf;
}

function makePng(color: [number, number, number]): Buffer {
  // Minimal valid PNG: 1x1 pixel via zlib-stored deflate of raw scanline
  const zlib = require('node:zlib') as typeof import('node:zlib');
  const raw = Buffer.from([0, color[0], color[1], color[2]]); // filter 0 + RGB
  const idat = zlib.deflateSync(raw);
  const chunks: Buffer[] = [];
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(1, 0); ihdr.writeUInt32BE(1, 4);
  ihdr[8] = 8; ihdr[9] = 2; // bit depth 8, color type 2 (RGB)
  chunks.push(sign('IHDR', ihdr));
  chunks.push(sign('IDAT', idat));
  chunks.push(sign('IEND', Buffer.alloc(0)));
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), ...chunks]);
}

function sign(type: string, data: Buffer): Buffer {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const body = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body), 0);
  return Buffer.concat([len, body, crc]);
}

const CRC_TABLE: number[] = (() => {
  const table: number[] = [];
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(buf: Buffer): number {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

const VERSION = '1.16.1';

export default async function mockSubsonic(app: FastifyInstance): Promise<void> {
  const colorFor = (id: string): [number, number, number] => {
    const h = crc32(Buffer.from(id)) % 360;
    const to255 = (x: number) => Math.max(0, Math.min(255, Math.round(x * 255)));
    return [to255((h / 360) * 0.5 + 0.2), to255(0.3 + (h % 60) / 200), to255(0.6 - (h % 40) / 200)];
  };

  app.all('/rest/:view', async (request: FastifyRequest, reply: FastifyReply) => {
    const { view } = request.params as { view: string };
    const query = { ...(request.query as Record<string, unknown>), ...(request.body as Record<string, unknown> ?? {}) };
    if (!checkAuth(query)) return fail(reply, VERSION, 40, 'Wrong username or password');

    switch (view) {
      case 'ping.view':
        return envelope(reply, VERSION, {});

      case 'getRandomSongs.view': {
        const size = Number(query.size || 30);
        return envelope(reply, VERSION, {
          randomSongs: { song: SONGS.slice(0, Math.min(size, SONGS.length)).map(childOf) },
        });
      }

      case 'getAlbumList2.view': {
        const albums = new Map<string, { id: string; name: string; artist: string; songCount: number; duration: number; coverArt: string }>();
        for (const s of SONGS) {
          const id = 'album-' + s.album;
          if (!albums.has(id)) albums.set(id, { id, name: s.album, artist: s.artist, songCount: 0, duration: 0, coverArt: s.coverArt });
          const a = albums.get(id)!;
          a.songCount++; a.duration += s.duration;
        }
        return envelope(reply, VERSION, { albumList2: { album: [...albums.values()] } });
      }

      case 'search3.view': {
        const q = String(query.query || '').toLowerCase();
        const songs = SONGS.filter(s =>
          s.title.toLowerCase().includes(q) ||
          s.artist.toLowerCase().includes(q) ||
          s.album.toLowerCase().includes(q)
        );
        const artists = [...new Set(songs.map(s => s.artist))].map((name, i) => ({ id: 'artist-' + name, name }));
        const albums = [...new Set(songs.map(s => s.album))].map((name, i) => ({ id: 'album-' + name, name }));
        return envelope(reply, VERSION, {
          searchResult3: {
            song: songs.map(childOf),
            artist: artists,
            album: albums,
          },
        });
      }

      case 'getCoverArt.view': {
        const id = String(query.id || '');
        const song = SONGS.find(s => s.coverArt === id);
        if (!song) return fail(reply, VERSION, 70, 'Cover art not found');
        reply.header('Content-Type', 'image/png').send(makePng(colorFor(id)));
        return reply;
      }

      case 'stream.view': {
        const id = String(query.id || '');
        const song = SONGS.find(s => s.id === id);
        if (!song) return fail(reply, VERSION, 70, 'Song not found');
        const wav = makeWav(3);
        reply.header('Content-Type', 'audio/wav').header('Content-Length', wav.length).send(wav);
        return reply;
      }

      case 'getLyricsBySongId.view': {
        const id = String(query.songId || query.id || '');
        const song = SONGS.find(s => s.id === id);
        if (!song) return fail(reply, VERSION, 70, 'Song not found');
        if (!song.lyrics) return envelope(reply, VERSION, { lyricsList: { structuredLyrics: [] } });
        return envelope(reply, VERSION, {
          lyricsList: {
            structuredLyrics: [{
              lang: 'xxx', synced: true,
              line: song.lyrics.split('\n').map((text) => {
                const m = text.match(/^\[(\d{2}):(\d{2})\.(\d{2})\](.*)$/);
                if (m) return { start: (Number(m[1]) * 60 + Number(m[2])) * 1000 + Number(m[3]) * 10, value: m[4] };
                return { value: text };
              }),
            }],
          },
        });
      }

      case 'getLyrics.view': {
        const artist = String(query.artist || '').toLowerCase();
        const title = String(query.title || '').toLowerCase();
        const song = SONGS.find(s => s.title.toLowerCase() === title && (!artist || s.artist.toLowerCase() === artist));
        if (!song || !song.lyrics) return envelope(reply, VERSION, { lyrics: { value: '' } });
        return envelope(reply, VERSION, { lyrics: { artist: song.artist, title: song.title, value: song.lyrics } });
      }

      case 'scrobble.view': {
        const id = String(query.id || '');
        if (!SONGS.some(s => s.id === id)) return fail(reply, VERSION, 70, 'Song not found');
        return envelope(reply, VERSION, {});
      }

      case 'star.view': {
        const ids = [].concat(query.id as never).filter(Boolean) as string[];
        for (const id of ids) if (SONGS.some(s => s.id === id)) starred.add(id);
        return envelope(reply, VERSION, {});
      }

      case 'unstar.view': {
        const ids = [].concat(query.id as never).filter(Boolean) as string[];
        for (const id of ids) starred.delete(id);
        return envelope(reply, VERSION, {});
      }

      case 'getStarred2.view':
        return envelope(reply, VERSION, { starred2: { song: SONGS.filter(s => starred.has(s.id)).map(childOf) } });

      default:
        return fail(reply, VERSION, 70, 'Unknown endpoint');
    }
  });
}
