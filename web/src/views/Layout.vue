<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { authApi } from '../api/endpoints'
import { clearUser, isAdmin, userStore } from '../store/user'
import { removeAdminRoutes } from '../router'

const router = useRouter()

async function onLogout(): Promise<void> {
  try {
    await authApi.logout()
  } catch {
    /* 服务端会话可能已失效，本地照样清理 */
  }
  clearUser()
  removeAdminRoutes()
  await router.push('/login')
  ElMessage.success('已退出登录')
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="230px" class="aside">
      <div class="brand">🎵 MusicPipe 管理</div>
      <!-- 侧边导航按角色条件渲染：user 只见仪表盘 -->
      <el-menu :default-active="$route.path" router class="menu">
        <el-menu-item index="/dashboard">仪表盘</el-menu-item>
        <template v-if="isAdmin()">
          <el-menu-item index="/users">用户管理</el-menu-item>
          <el-menu-item index="/releases">发布管理</el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="who">
          {{ userStore.user?.username }}
          <el-tag size="small" :type="isAdmin() ? 'danger' : 'info'">{{ userStore.user?.role }}</el-tag>
        </span>
        <el-button size="small" text type="danger" @click="onLogout">退出登录</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: #fff;
  border-right: 1px solid #e4e7ed;
}
.brand {
  padding: 18px 16px;
  font-weight: 600;
  font-size: 16px;
  color: #db2c1f;
  white-space: nowrap;
}
.menu {
  border-right: none;
}
.header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.who {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #303133;
}
</style>
