import http from './http'
import type { ApiUserInfo, Paged, ReleaseItem, UserRole, UserRow } from '../types'

/** §3.2 认证 */
export const authApi = {
  login(body: { username: string; password: string }): Promise<ApiUserInfo> {
    return http.post<ApiUserInfo>('/api/auth/login', body).then((r) => r.data)
  },
  register(body: { username: string; password: string }): Promise<ApiUserInfo> {
    return http.post<ApiUserInfo>('/api/auth/register', body).then((r) => r.data)
  },
  logout(): Promise<void> {
    return http.post<void>('/api/auth/logout').then(() => undefined)
  },
  me(): Promise<ApiUserInfo> {
    return http.get<ApiUserInfo>('/api/auth/me').then((r) => r.data)
  },
}

/** §3.3 用户管理（仅 admin） */
export const usersApi = {
  list(page: number, limit: number): Promise<Paged<UserRow>> {
    return http.get<Paged<UserRow>>('/api/users', { params: { page, limit } }).then((r) => r.data)
  },
  create(body: { username: string; password: string; role: UserRole }): Promise<ApiUserInfo> {
    return http.post<ApiUserInfo>('/api/users', body).then((r) => r.data)
  },
  /** PUT /api/users/:id：body {password?, role?} 至少一项（本页面始终带 role） */
  update(id: number, body: { password?: string; role?: UserRole }): Promise<ApiUserInfo> {
    return http.put<ApiUserInfo>(`/api/users/${id}`, body).then((r) => r.data)
  },
  remove(id: number): Promise<void> {
    return http.delete<void>(`/api/users/${id}`).then(() => undefined)
  },
}

/** §3.4 更新服务（发布管理，仅 admin） */
export const releasesApi = {
  list(page: number, limit: number): Promise<Paged<ReleaseItem>> {
    return http
      .get<Paged<ReleaseItem>>('/api/app/releases', { params: { page, limit } })
      .then((r) => r.data)
  },
  /** multipart：字段 versionName/versionCode/changelog + 文件字段 file(.apk)；
   *  Content-Type 交给浏览器按 FormData 自动生成（含 boundary） */
  create(fd: FormData): Promise<ReleaseItem> {
    return http.post<ReleaseItem>('/api/app/releases', fd).then((r) => r.data)
  },
  remove(id: number): Promise<void> {
    return http.delete<void>(`/api/app/releases/${id}`).then(() => undefined)
  },
}

/** downloadUrl 为 /api/... 相对路径（契约 §3.4）；跨源部署时补 VITE_API_BASE 前缀 */
export function withBase(path: string): string {
  return `${import.meta.env.VITE_API_BASE ?? ''}${path}`
}
