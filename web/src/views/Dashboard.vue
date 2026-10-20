<script setup lang="ts">
import { isAdmin, userStore } from '../store/user'
</script>

<template>
  <div>
    <h2>你好，{{ userStore.user?.username }}</h2>
    <el-alert
      v-if="isAdmin()"
      type="success"
      :closable="false"
      title="你当前是管理员"
      description="可在左侧「用户管理」维护账号，在「发布管理」上传 APK 更新包。"
      class="tip"
    />
    <!-- 契约：user 角色登录后只显示空白仪表盘/提示 -->
    <el-alert
      v-else
      type="info"
      :closable="false"
      title="普通用户"
      description="该账号没有管理权限。如需管理用户或发布 App 更新，请联系管理员。"
      class="tip"
    />
  </div>
</template>

<style scoped>
.tip {
  max-width: 640px;
  margin-top: 8px;
}
</style>
