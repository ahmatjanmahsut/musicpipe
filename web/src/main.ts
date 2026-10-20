import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router, { syncAdminRoutes } from './router'
import { authApi } from './api/endpoints'
import { setUser, userStore } from './store/user'

// 冷启动：按本地登录态恢复动态 admin 路由；再用 /api/auth/me 校验会话（401 由拦截器清态跳登录）
syncAdminRoutes()
if (userStore.user) {
  authApi
    .me()
    .then((u) => {
      setUser(u)
      syncAdminRoutes()
    })
    .catch(() => {
      /* 网络错误：保留本地态，后续请求 401 时由拦截器兜底 */
    })
}

createApp(App).use(router).use(ElementPlus).mount('#app')
