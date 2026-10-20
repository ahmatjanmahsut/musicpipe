import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import router, { removeAdminRoutes } from '../router'
import { clearUser } from '../store/user'
import type { ApiErrorBody } from '../types'

/**
 * 统一 axios 实例：
 * - baseURL = import.meta.env.VITE_API_BASE，缺省 ''（同源 /api —— 生产由 server 托管 web/dist，
 *   开发期走 vite proxy /api -> http://localhost:7800）
 * - withCredentials: true（mp_session cookie）
 * - 不设 timeout：APK 上传最大 200MB，不能截断
 */
export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? '',
  withCredentials: true,
})

/** 业务错误：保留契约 §3.0 的 machine code（如 last_admin/duplicate_version_code）与 HTTP status */
export class ApiError extends Error {
  readonly code: string
  readonly status: number

  constructor(message: string, code: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

function toApiError(err: unknown): ApiError {
  if (err instanceof ApiError) return err
  if (err instanceof AxiosError) {
    const status = err.response?.status ?? 0
    const body = err.response?.data as ApiErrorBody | undefined
    const message = body?.message ?? (status ? `请求失败（HTTP ${status}）` : '网络错误：无法连接服务器')
    return new ApiError(message, body?.error ?? 'network_error', status)
  }
  return new ApiError(err instanceof Error ? err.message : '未知错误', 'internal_error', 0)
}

http.interceptors.response.use(
  (res) => res,
  (err: unknown) => {
    const apiErr = toApiError(err)
    if (apiErr.status === 401) {
      // 会话失效/未登录：清登录态（含动态 admin 路由）并跳 /login
      clearUser()
      removeAdminRoutes()
      if (router.currentRoute.value.name !== 'login') {
        void router.push({ path: '/login' })
      }
    }
    // 统一 ElMessage 展示后端 body.message（含 403/409 last_admin 等；
    // 登录页的 401 wrong_password 同样走到这里提示）
    ElMessage.error(apiErr.message)
    return Promise.reject(apiErr)
  },
)

export default http
