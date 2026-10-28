<template>
  <div class="profile-page">
    <page-header
      title="我的"
      icon="User"
      description="GET /api/v1/auth/me · POST /api/v1/files/upload · PUT /api/v1/auth/avatar · GET /api/v1/credit/logs"
    >
      <template #extra>
        <el-button :icon="Refresh" :loading="loading" @click="loadProfile">刷新</el-button>
        <el-button type="danger" :icon="SwitchButton" @click="onLogout">退出登录</el-button>
      </template>
    </page-header>

    <div class="page-container">
      <el-card shadow="never" class="page-card" v-loading="loading">
        <template #header>
          <div class="card-header"><span>账号信息</span></div>
        </template>
        <div class="profile-head">
          <el-upload
            class="avatar-uploader"
            accept="image/*"
            :show-file-list="false"
            :before-upload="beforeAvatarUpload"
            :http-request="handleAvatarUpload"
            :disabled="avatarUploading"
          >
            <div class="avatar-wrap" v-loading="avatarUploading">
              <el-avatar :size="56" :src="me.avatar || undefined" class="profile-avatar">
                <template v-if="!me.avatar">{{ avatarText }}</template>
              </el-avatar>
              <div class="avatar-mask">
                <el-icon :size="16"><Camera /></el-icon>
                <span>更换</span>
              </div>
            </div>
          </el-upload>
          <div>
            <div class="profile-name">
              {{ me.nickname || me.username || '-' }}
              <el-tag size="small" :type="me.role === 'SYS_ADMIN' ? 'danger' : 'primary'">
                {{ roleLabel(me.role) }}
              </el-tag>
              <el-tag size="small" :type="Number(me.status) === 1 ? 'success' : 'danger'">
                {{ Number(me.status) === 1 ? '正常' : '禁用' }}
              </el-tag>
            </div>
            <div class="text-muted mt-8">用户名：{{ me.username || '-' }}</div>
            <div class="text-muted mt-8">点击头像上传新头像（支持 image/*，大小不超过 2MB）</div>
          </div>
        </div>

        <el-descriptions :column="3" border size="small" class="mt-8">
          <el-descriptions-item label="用户 ID">{{ me.id ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="学号/工号">{{ me.studentNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ me.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="信用分">{{ me.creditScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="违约次数">{{ me.violateCount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="黑名单截止">
            {{ me.blacklistUntil || '无' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>信用分流水（GET /credit/logs）</span>
            <el-button size="small" :icon="Refresh" :loading="logLoading" @click="loadLogs">刷新</el-button>
          </div>
        </template>
        <el-table :data="logs" v-loading="logLoading" border stripe size="small">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="变动值" width="100" align="center">
            <template #default="{ row }">
              <span :class="Number(row.changeValue) >= 0 ? 'text-success' : 'text-danger'">
                {{ Number(row.changeValue) > 0 ? '+' : '' }}{{ row.changeValue }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="afterScore" label="变动后分值" width="110" align="center" />
          <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
          <el-table-column label="关联业务" width="150">
            <template #default="{ row }">
              <span class="text-muted">{{ row.bizType || '-' }} #{{ row.bizId ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="180" show-overflow-tooltip />
          <template #empty>
            <el-empty description="暂无信用分流水" :image-size="80" />
          </template>
        </el-table>
        <div class="pagination-wrap">
          <el-pagination
            background
            layout="total, prev, pager, next"
            :total="logTotal"
            :current-page="logPageNum"
            :page-size="logPageSize"
            @current-change="onLogPageChange"
          />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Camera, Refresh, SwitchButton } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { fetchCreditLogs } from '@/api/user'
import { uploadFile } from '@/api/file'
import { updateAvatar } from '@/api/auth'
import { useUserStore } from '@/store/user'
import { useNoticeStore } from '@/store/notice'
import { roleLabel } from '@/utils/format'

const AVATAR_MAX_SIZE = 2 * 1024 * 1024

const router = useRouter()
const userStore = useUserStore()
const noticeStore = useNoticeStore()

const loading = ref(false)
const avatarUploading = ref(false)
const me = ref({ ...(userStore.userInfo || {}) })

const logs = ref([])
const logLoading = ref(false)
const logTotal = ref(0)
const logPageNum = ref(1)
const logPageSize = ref(10)

const avatarText = computed(() => (me.value.nickname || me.value.username || 'A').slice(0, 1).toUpperCase())

/** 头像前置校验：仅图片、≤2MB */
function beforeAvatarUpload (file) {
  if (!file) return false
  if (!String(file.type || '').startsWith('image/')) {
    ElMessage.error('仅支持上传图片文件（image/*）')
    return false
  }
  if (file.size > AVATAR_MAX_SIZE) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

/**
 * 头像上传（自定义 http-request）：
 * 1) POST /api/v1/files/upload（FormData: file + dir=avatar）→ 文件 URL
 * 2) PUT /api/v1/auth/avatar {url} → 返回最新用户信息
 * 3) 同步本地 user 存储（含刷新后仍生效）与页面展示
 */
async function handleAvatarUpload (options) {
  avatarUploading.value = true
  try {
    const uploaded = await uploadFile(options.file, 'avatar')
    const url = typeof uploaded === 'string' ? uploaded : (uploaded?.url || '')
    if (!url) {
      ElMessage.error('上传接口未返回文件 URL')
      options.onError?.(new Error('上传接口未返回文件 URL'))
      return
    }
    const user = await updateAvatar(url)
    const avatar = user?.avatar || url
    me.value = { ...me.value, ...(user || {}), avatar }
    userStore.patchUser({ ...(user || {}), avatar })
    ElMessage.success('头像已更新')
    options.onSuccess?.(uploaded)
  } catch (e) {
    // 请求层已用 ElMessage 展示后端 message
    options.onError?.(e)
  } finally {
    avatarUploading.value = false
  }
}

async function loadProfile () {
  loading.value = true
  try {
    const data = await userStore.fetchProfile()
    me.value = { ...(data || {}) }
  } catch (e) {
    // 请求层已提示
  } finally {
    loading.value = false
  }
}

async function loadLogs () {
  logLoading.value = true
  try {
    const page = await fetchCreditLogs({ pageNum: logPageNum.value, pageSize: logPageSize.value })
    logs.value = page?.records || []
    logTotal.value = Number(page?.total ?? logs.value.length)
  } catch (e) {
    logs.value = []
    logTotal.value = 0
  } finally {
    logLoading.value = false
  }
}

function onLogPageChange (page) {
  logPageNum.value = page
  loadLogs()
}

async function onLogout () {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await userStore.logout()
  noticeStore.reset()
  ElMessage.success('已退出登录')
  router.replace('/login')
}

onMounted(() => {
  loadProfile()
  loadLogs()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar-uploader {
  display: block;
  line-height: 0;
}

.avatar-wrap {
  position: relative;
  display: inline-block;
  border-radius: 50%;
  cursor: pointer;
  overflow: hidden;
}

.avatar-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 12px;
  line-height: 1.1;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-wrap:hover .avatar-mask {
  opacity: 1;
}

.profile-avatar {
  background-color: var(--cv-primary);
  color: #fff;
  font-size: 20px;
}

.profile-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 600;
}

.text-success {
  color: #67c23a;
}

.text-danger {
  color: #f56c6c;
}
</style>
