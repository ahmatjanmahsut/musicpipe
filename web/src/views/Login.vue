<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { authApi } from '../api/endpoints'
import { registerAdminRoutes } from '../router'
import { setUser, type UserRole } from '../store/user'
import type { ApiUserInfo } from '../types'

const router = useRouter()
const activeTab = ref<'login' | 'register'>('login')

// 契约 §3.2：用户名 3-32 位（字母/数字/下划线/中文），密码 >= 6 位
const USERNAME_RE = /^[A-Za-z0-9_\u4e00-\u9fa5]{3,32}$/

const loginRef = ref<FormInstance>()
const loginForm = reactive({ username: '', password: '' })
const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const regRef = ref<FormInstance>()
const regForm = reactive({ username: '', password: '', confirm: '' })
const regRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: USERNAME_RE, message: '用户名须为 3-32 位字母/数字/下划线/中文', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_r, v: string, cb: (e?: Error) => void) =>
        v === regForm.password ? cb() : cb(new Error('两次输入的密码不一致')),
      trigger: 'blur',
    },
  ],
}

const submitting = ref(false)

function afterAuthed(u: ApiUserInfo): void {
  setUser(u)
  // admin 才注册管理路由；user 角色只进仪表盘（提示无权限）
  if (u.role === 'admin') registerAdminRoutes()
  if (u.role === 'admin') {
    void router.push('/users')
  } else {
    void router.push('/dashboard')
    ElMessage.info('登录成功。当前账号为普通用户，无管理权限。')
  }
}

async function submitLogin(): Promise<void> {
  const form = loginRef.value
  if (!form) return
  const ok = await form.validate().catch(() => false)
  if (ok !== true) return
  submitting.value = true
  try {
    const u = await authApi.login({ ...loginForm })
    afterAuthed(u)
  } catch {
    /* 错误已由拦截器统一 ElMessage 展示 body.message（user_not_found / wrong_password） */
  } finally {
    submitting.value = false
  }
}

async function submitRegister(): Promise<void> {
  const form = regRef.value
  if (!form) return
  const ok = await form.validate().catch(() => false)
  if (ok !== true) return
  submitting.value = true
  try {
    let u = await authApi.register({ username: regForm.username, password: regForm.password })
    // 契约只给 login 明确了 Set-Cookie；register 成功后再走一次 login 以确保拿到会话，
    // 若 login 意外失败则沿用 register 响应（服务端 register 若已下发 cookie 则直接可用）
    try {
      u = await authApi.login({ username: regForm.username, password: regForm.password })
    } catch {
      /* keep register result */
    }
    afterAuthed({ id: u.id, username: u.username, role: u.role as UserRole })
  } catch {
    /* 409 username_taken / 400 invalid_params 由拦截器展示 */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page">
    <el-card class="card" shadow="always">
      <template #header>
        <div class="title">🎵 MusicPipe 管理控制台</div>
      </template>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="登录" name="login">
          <el-form ref="loginRef" :model="loginForm" :rules="loginRules" label-width="72px" @submit.prevent>
            <el-form-item label="用户名" prop="username">
              <el-input v-model="loginForm.username" autocomplete="username" @keyup.enter="submitLogin" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="loginForm.password" type="password" show-password autocomplete="current-password" @keyup.enter="submitLogin" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="submitLogin">登 录</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form ref="regRef" :model="regForm" :rules="regRules" label-width="72px" @submit.prevent>
            <el-form-item label="用户名" prop="username">
              <el-input v-model="regForm.username" autocomplete="username" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="regForm.password" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirm">
              <el-input v-model="regForm.confirm" type="password" show-password autocomplete="new-password" @keyup.enter="submitRegister" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="submitRegister">注 册</el-button>
            </el-form-item>
          </el-form>
          <el-alert type="info" :closable="false" class="hint" title="库中第一个注册的用户将自动成为管理员" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fff5f4 0%, #f5f7fa 60%);
}
.card {
  width: 420px;
}
.title {
  font-weight: 600;
  color: #db2c1f;
}
.hint {
  margin-top: -8px;
}
</style>
