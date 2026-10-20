function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n)
}

/** unix ms -> YYYY-MM-DD HH:mm:ss（本地时区） */
export function formatDateTime(ms: number | undefined | null): string {
  if (typeof ms !== 'number' || !Number.isFinite(ms) || ms <= 0) return '-'
  const d = new Date(ms)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 字节数 -> 人类可读（1024 进制） */
export function formatSize(bytes: number | undefined | null): string {
  if (typeof bytes !== 'number' || !Number.isFinite(bytes) || bytes < 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let v = bytes
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i += 1
  }
  return `${i === 0 ? String(v) : v.toFixed(2)} ${units[i]}`
}
