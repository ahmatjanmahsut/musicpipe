import path from 'node:path';

export interface AppConfig {
  port: number;
  dataDir: string;
  sessionTtlMs: number;
  mockSubsonic: boolean;
  portSub: number;
}

export function loadConfig(overrides?: Partial<AppConfig>): AppConfig {
  return {
    port: overrides?.port ?? Number(process.env.PORT || 7800),
    dataDir: overrides?.dataDir ?? (process.env.MUSICPIPE_DATA || path.resolve(process.cwd(), 'data')),
    sessionTtlMs: overrides?.sessionTtlMs ?? Number(process.env.SESSION_TTL_MS || 7 * 24 * 3600 * 1000),
    mockSubsonic: overrides?.mockSubsonic ?? process.env.MOCK_SUBSONIC === '1',
    portSub: overrides?.portSub ?? Number(process.env.PORT_SUB || 4533),
  };
}
