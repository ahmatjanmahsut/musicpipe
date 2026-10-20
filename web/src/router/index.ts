import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { isAdmin, userStore } from '../store/user'

const baseRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    name: 'root',
    component: () => import('../views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '控制台' },
      },
    ],
  },
  {
    path: '/forbidden',
    name: 'forbidden',
    component: () => import('../views/Forbidden.vue'),
    meta: { title: '无权限' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('../views/NotFound.vue'),
    meta: { title: '页面不存在' },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes: baseRoutes,
})

/**
 * admin 专属路由（契约 §3.3/§3.4 均为 admin 门禁接口）。
 * 不静态注册：role=user 时路由表中不存在这些记录，任何导航都只能落进通配 NotFound；
 * 登录后动态 addRoute，登出/角色变化后 removeRoute（任务要求「彻底隐藏，非置灰」）。
 */
const ADMIN_ROUTES: RouteRecordRaw[] = [
  {
    path: '/users',
    name: 'users',
    component: () => import('../views/Users.vue'),
    meta: { requiresAdmin: true, title: '用户管理' },
  },
  {
    path: '/releases',
    name: 'releases',
    component: () => import('../views/Releases.vue'),
    meta: { requiresAdmin: true, title: '发布管理' },
  },
]

let adminRegistered = false

export function registerAdminRoutes(): void {
  if (adminRegistered) return
  for (const r of ADMIN_ROUTES) router.addRoute('root', r)
  adminRegistered = true
}

export function removeAdminRoutes(): void {
  if (!adminRegistered) return
  for (const r of ADMIN_ROUTES) router.removeRoute(String(r.name))
  adminRegistered = false
}

/** 登录态/角色变化后调用：admin 注册、非 admin 移除（角色变更立即生效） */
export function syncAdminRoutes(): void {
  if (isAdmin()) registerAdminRoutes()
  else removeAdminRoutes()
}

router.beforeEach((to) => {
  const loggedIn = !!userStore.user
  if (to.meta.public) {
    // 已登录再访问 /login 直接回首页
    return to.name === 'login' && loggedIn ? { path: '/' } : true
  }
  // 无登录态 -> /login
  if (!loggedIn) {
    return { path: '/login', query: to.fullPath === '/' ? undefined : { redirect: to.fullPath } }
  }
  // 防御兜底（动态路由已保证 user 进不来，此处覆盖时序竞态）
  if (to.meta.requiresAdmin && !isAdmin()) {
    return { path: '/forbidden' }
  }
  return true
})

router.afterEach((to) => {
  const t = (to.meta.title as string) || ''
  document.title = t ? `${t} · MusicPipe 管理` : 'MusicPipe 管理'
})

export default router
