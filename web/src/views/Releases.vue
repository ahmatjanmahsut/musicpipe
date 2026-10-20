<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  ElMessage,
  ElMessageBox,
  genFileId,
  type FormInstance,
  type FormRules,
  type UploadFile,
  type UploadInstance,
  type UploadRawFile,
} from 'element-plus'
import { releasesApi, withBase } from '../api/endpoints'
import { formatDateTime, formatSize } from '../utils/format'
import type { ReleaseItem } from '../types'

const MAX_APK_BYTES = 200 * 1024 * 1024 // 契约 §3.4：<= 200MB（服务端也会校验 bad_apk）

const loading = ref(false)
const rows = ref<ReleaseItem[]>([])
const total = ref(0)
const page = ref(1)
const limit = ref(20)

/** GET /api/app/releases?page=&limit=（admin；按 versionCode 降序） */
async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await releasesApi.list(page.value, limit.value)
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

/* ---------- 上传对话框（el-upload 手动模式） ---------- */
const uploadVisible = ref(false)
const uploadFormRef = ref<FormInstance>()
const uploadRef = ref<UploadInstance>()
const uploading = ref(false)
const pickedFile = ref<File | null>(null)

const uploadForm = reactive<{ versionName: string; versionCode?: number; changelog: string }>({
  versionName: '',
  versionCode: undefined,
  changelog: '',
})

const uploadRules: FormRules = {
  versionName: [
    { required: true, message: '请输入版本名，如 1.2.0', trigger: 'blur' },
    { max: 64, message: '版本名不能超过 64 字符', trigger: 'blur' },
  ],
  versionCode: [
    { required: true, message: '请输入版本号（整数）', trigger: 'blur' },
    {
      validator: (_r, v: number | undefined, cb: (e?: Error) => void) =>
        typeof v === 'number' && Number.isInteger(v) && v > 0 ? cb() : cb(new Error('versionCode 须为正整数')),
      trigger: 'change',
    },
  ],
}

function openUpload(): void {
  uploadForm.versionName = ''
  uploadForm.versionCode = undefined
  uploadForm.changelog = ''
  pickedFile.value = null
  uploadRef.value?.clearFiles()
  uploadVisible.value = true
}

function isApk(name: string): boolean {
  return /\.apk$/i.test(name)
}

/** 手动模式：auto-upload=false，on-change 只做收集与 .apk/大小校验，提交由「上传」按钮走 axios multipart */
function onFileChange(file: UploadFile): void {
  const raw = file.raw
  if (!raw) return
  if (!isApk(raw.name)) {
    ElMessage.warning('只能选择 .apk 文件')
    pickedFile.value = null
    uploadRef.value?.clearFiles()
    return
  }
  if (raw.size > MAX_APK_BYTES) {
    ElMessage.warning('APK 超过 200MB 限制')
    pickedFile.value = null
    uploadRef.value?.clearFiles()
    return
  }
  pickedFile.value = raw
}

/** limit=1 时重新选择：替换旧文件 */
function onExceed(files: File[]): void {
  uploadRef.value?.clearFiles()
  pickedFile.value = null
  const file = files[0] as UploadRawFile
  file.uid = genFileId()
  uploadRef.value?.handleStart(file)
}

async function submitUpload(): Promise<void> {
  const form = uploadFormRef.value
  if (!form) return
  const ok = await form.validate().catch(() => false)
  if (ok !== true) return
  if (!pickedFile.value) {
    ElMessage.warning('请选择要上传的 .apk 文件')
    return
  }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('versionName', uploadForm.versionName)
    fd.append('versionCode', String(uploadForm.versionCode)) // multipart 文本字段，服务端按 int 解析
    fd.append('changelog', uploadForm.changelog)
    fd.append('file', pickedFile.value, pickedFile.value.name)
    const rel = await releasesApi.create(fd)
    // 任务要求：上传成功刷新列表并显示服务端计算的 md5
    ElMessage({ type: 'success', message: `上传成功 · ${rel.versionName}(${rel.versionCode}) · 服务端 MD5: ${rel.md5}`, duration: 6000 })
    uploadVisible.value = false
    page.value = 1
    await load()
  } catch {
    /* 409 duplicate_version_code / 400 bad_apk 的 message 已由拦截器展示 */
  } finally {
    uploading.value = false
  }
}

/* ---------- 删除发布 ---------- */
async function removeRelease(row: ReleaseItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除发布 ${row.versionName}（versionCode ${row.versionCode}）？磁盘上的 APK 文件将一并删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await releasesApi.remove(row.id)
    ElMessage.success('已删除')
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    await load()
  } catch {
    /* 404 release_not_found 已由拦截器展示 */
  }
}

/** 列表行若无 downloadUrl，按契约 §3.4 规则拼接 */
function downloadHref(row: ReleaseItem): string {
  return withBase(row.downloadUrl || `/api/app/releases/download?versionCode=${row.versionCode}`)
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <h3>发布管理（App 更新包）</h3>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="openUpload">上传 APK</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="versionName" label="版本名" width="130" />
      <el-table-column prop="versionCode" label="版本号" width="90" sortable />
      <el-table-column label="大小" width="110">
        <template #default="scope">{{ formatSize(scope.row.size) }}</template>
      </el-table-column>
      <el-table-column label="MD5" width="340">
        <template #default="scope"><code class="md5">{{ scope.row.md5 }}</code></template>
      </el-table-column>
      <el-table-column prop="changelog" label="更新说明" min-width="180" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.changelog || '—' }}</template>
      </el-table-column>
      <el-table-column label="发布时间" width="180">
        <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="scope">
          <el-link class="dl" type="primary" :href="downloadHref(scope.row)" target="_blank" :underline="false">下载</el-link>
          <el-button size="small" type="danger" plain @click="removeRelease(scope.row)">删除</el-button>
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

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadVisible" title="上传 APK 发布" width="560px" destroy-on-close @closed="pickedFile = null">
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="100px" @submit.prevent>
        <el-form-item label="版本名" prop="versionName">
          <el-input v-model="uploadForm.versionName" placeholder="如 1.2.0" />
        </el-form-item>
        <el-form-item label="版本号" prop="versionCode">
          <el-input-number v-model="uploadForm.versionCode" :min="1" :step="1" :precision="0" controls-position="right" placeholder="整数，App 端据此比对更新" />
        </el-form-item>
        <el-form-item label="APK 文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".apk"
            drag
            :on-change="onFileChange"
            :on-remove="() => (pickedFile = null)"
            :on-exceed="onExceed"
          >
            <div class="up-text">将 .apk 拖到此处，或<em>点击选择</em>（最大 200MB）</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="更新说明" prop="changelog">
          <el-input v-model="uploadForm.changelog" type="textarea" :rows="3" placeholder="可空，App 更新对话框展示" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!pickedFile" @click="submitUpload">上传</el-button>
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
.md5 {
  font-family: Consolas, Menlo, monospace;
  font-size: 12px;
  color: #606266;
  word-break: break-all;
}
.dl {
  margin-right: 10px;
}
.up-text em {
  color: var(--el-color-primary);
  font-style: normal;
}
</style>
