/** 契约 §3 数据模型（字段命名与后端响应体逐字一致） */

export type UserRole = 'admin' | 'user'

/** POST /api/auth/login、/api/auth/register、/api/auth/me、POST/PUT /api/users 的返回 */
export interface ApiUserInfo {
  id: number
  username: string
  role: UserRole
}

/** GET /api/users 列表行（created_at 为 unix ms） */
export interface UserRow {
  id: number
  username: string
  role: UserRole
  created_at: number
}

/** 列表统一信封 { items, total }（契约 §3.0） */
export interface Paged<T> {
  items: T[]
  total: number
}

/** 发布记录（契约 §3.4：camelCase；createdAt 为 unix ms） */
export interface ReleaseItem {
  id: number
  versionName: string
  versionCode: number
  md5: string
  size: number
  changelog: string
  downloadUrl?: string
  createdAt: number
}

/** 统一错误体（契约 §3.0） */
export interface ApiErrorBody {
  error?: string
  message?: string
}
