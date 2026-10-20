<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <el-result icon="error" title="403" sub-title="无权限：该功能仅管理员可用">
    <template #extra>
      <el-button type="primary" @click="router.push('/')">返回首页</el-button>
    </template>
  </el-result>
</template>
