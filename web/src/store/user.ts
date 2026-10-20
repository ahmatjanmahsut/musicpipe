import { reactive } from 'vue'
import type { ApiUserInfo, UserRole } from '../types'

const STORAGE_KEY = 'musicpipe_user'

/** 登录态：localStorage（持久化）+ 内存响应式 store 双写（任务要求） */
export const userStore = reactive<{ user: ApiUserInfo | null }>({ user: loadStored() })

function loadStored(): ApiUserInfo | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const p: unknown = JSON.parse(raw)
    if (
      p &&
      typeof p === 'object' &&
      typeof (p as ApiUserInfo).id === 'number' &&
      typeof (p as ApiUserInfo).username === 'string' &&
      ((p as ApiUserInfo).role === 'admin' || (p as ApiUserInfo).role === 'user')
    ) {
      const u = p as ApiUserInfo
      return { id: u.id, username: u.username, role: u.role }
    }
  } catch {
    /* 损坏的本地数据按未登录处理 */
  }
  return null
}

export function setUser(user: ApiUserInfo): void {
  userStore.user = { id: user.id, username: user.username, role: user.role }
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(userStore.user))
  } catch {
    /* localStorage 不可用时仅保留内存态 */
  }
}

export function clearUser(): void {
  userStore.user = null
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* ignore */
  }
}

export function isAdmin(): boolean {
  return userStore.user?.role === 'admin'
}

export type { UserRole }
