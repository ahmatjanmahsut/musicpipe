<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { usersApi } from '../api/endpoints'
import { formatDateTime } from '../utils/format'
import { userStore } from '../store/user'
import type { UserRow, UserRole } from '../types'

const loading = ref(false)
const rows = ref<UserRow[]>([])
const total = ref(0)
const page = ref(1)
const limit = ref(20)

/** GET /api/users?page=&limit=（契约 §3.3，按 id 升序） */
async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await usersApi.list(page.value, limit.value)
    rows.value = data.items ?? []
    total.value = data.total ?? 0
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    loading.value = false
  }
}
onMounted(load)

function onPageChange(p: number): void {
  page.value = p
  void load()
}
function onSizeChange(s: number): void {
  limit.value = s
  page.value = 1
  void load()
}

/* ---------- 新增 ---------- */
const addVisible = ref(false)
const addRef = ref<FormInstance>()
const addForm = reactive<{ username: string; password: string; role: UserRole }>({
  username: '',
  password: '',
  role: 'user',
})
const USERNAME_RE = /^[A-Za-z0-9_\u4e00-\u9fa5]{3,32}$/
const addRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: USERNAME_RE, message: '用户名须为 3-32 位字母/数字/下划线/中文', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}
const submitting = ref(false)

function openAdd(): void {
  addForm.username = ''
  addForm.password = ''
  addForm.role = 'user'
  addRef.value?.clearValidate()
  addVisible.value = true
}

async function submitAdd(): Promise<void> {
  const form = addRef.value
  if (!form) return
  const ok = await form.validate().catch(() => false)
  if (ok !== true) return
  submitting.value = true
  try {
    await usersApi.create({ ...addForm })
    ElMessage.success(`已创建用户 ${addForm.username}`)
    addVisible.value = false
    await load()
  } catch {
    /* 409 username_taken 等 message 已由拦截器展示 */
  } finally {
    submitting.value = false
  }
}

/* ---------- 编辑（改角色 / 重置密码） ---------- */
const editVisible = ref(false)
const editRef = ref<FormInstance>()
const editForm = reactive<{ id: number; username: string; role: UserRole; password: string }>({
  id: 0,
  username: '',
  role: 'user',
  password: '',
})
const editRules: FormRules = {
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [
    {
      validator: (_r, v: string, cb: (e?: Error) => void) =>
        !v || v.length >= 6 ? cb() : cb(new Error('新密码至少 6 位')),
      trigger: 'blur',
    },
  ],
}

function openEdit(row: UserRow): void {
  editForm.id = row.id
  editForm.username = row.username
  editForm.role = row.role
  editForm.password = ''
  editRef.value?.clearValidate()
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  const form = editRef.value
  if (!form) return
  const ok = await form.validate().catch(() => false)
  if (ok !== true) return
  submitting.value = true
  try {
    // PUT body {password?, role?} 至少一项：role 始终携带，password 仅填写时携带
    const body: { role: UserRole; password?: string } = { role: editForm.role }
    if (editForm.password) body.password = editForm.password
    await usersApi.update(editForm.id, body)
    ElMessage.success('已保存')
    editVisible.value = false
    // 若改的是自己且降了角色，本地角色也要刷新
    if (userStore.user?.id === editForm.id && body.role !== userStore.user?.role) {
      userStore.user = { ...userStore.user, role: body.role }
      try { localStorage.setItem('musicpipe_user', JSON.stringify(userStore.user)) } catch { /* ignore */ }
    }
    await load()
  } catch {
    /* 409 last_admin（最后一名 admin 降级）等 message 已由拦截器直接展示 */
  } finally {
    submitting.value = false
  }
}

/* ---------- 删除 ---------- */
async function removeUser(row: UserRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.username}」？该操作不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      confirmButtonClass: 'el-button--danger',
      cancelButtonText: '取消',
    })
  } catch {
    return // 取消
  }
  try {
    await usersApi.remove(row.id)
    ElMessage.success('已删除')
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    await load()
  } catch {
    /* 400 cannot_delete_self / 409 last_admin / 404 user_not_found 的 message 已由拦截器展示 */
  }
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <h3>用户管理</h3>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="openAdd">新增用户</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="username" label="用户名" min-width="180" />
      <el-table-column label="角色" width="110">
        <template #default="scope">
          <el-tag size="small" :type="scope.row.role === 'admin' ? 'danger' : 'info'">{{ scope.row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="200">
        <template #default="scope">{{ formatDateTime(scope.row.created_at) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="scope">
          <el-button size="small" @click="openEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="removeUser(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, sizes, prev, pager, next, jumper"
      :total="total"
      :current-page="page"
      :page-size="limit"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />

    <!-- 新增对话框 -->
    <el-dialog v-model="addVisible" title="新增用户" width="440px" destroy-on-close>
      <el-form ref="addRef" :model="addForm" :rules="addRules" label-width="80px" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="addForm.username" placeholder="3-32 位字母/数字/下划线/中文" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="addForm.password" type="password" show-password placeholder="至少 6 位" autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="addForm.role" style="width: 100%">
            <el-option label="user（普通用户）" value="user" />
            <el-option label="admin（管理员）" value="admin" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAdd">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑对话框：改角色 + 重置密码 -->
    <el-dialog v-model="editVisible" title="编辑用户" width="440px" destroy-on-close>
      <el-form ref="editRef" :model="editForm" :rules="editRules" label-width="90px" @submit.prevent>
        <el-form-item label="用户名">
          <el-input :model-value="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="editForm.role" style="width: 100%">
            <el-option label="user（普通用户）" value="user" />
            <el-option label="admin（管理员）" value="admin" />
          </el-select>
        </el-form-item>
        <el-form-item label="重置密码" prop="password">
          <el-input v-model="editForm.password" type="password" show-password placeholder="留空则不修改密码" autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.toolbar h3 {
  margin: 0;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
