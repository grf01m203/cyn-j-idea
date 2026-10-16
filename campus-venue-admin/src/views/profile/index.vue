<template>
  <div class="profile-page">
    <page-header
      title="我的"
      icon="User"
      description="GET /api/v1/auth/me · POST /api/v1/auth/logout · GET /api/v1/credit/logs"
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
          <el-avatar :size="56" class="profile-avatar">{{ avatarText }}</el-avatar>
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
import { Refresh, SwitchButton } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { fetchCreditLogs } from '@/api/user'
import { useUserStore } from '@/store/user'
import { useNoticeStore } from '@/store/notice'
import { roleLabel } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()
const noticeStore = useNoticeStore()

const loading = ref(false)
const me = ref({ ...(userStore.userInfo || {}) })

const logs = ref([])
const logLoading = ref(false)
const logTotal = ref(0)
const logPageNum = ref(1)
const logPageSize = ref(10)

const avatarText = computed(() => (me.value.nickname || me.value.username || 'A').slice(0, 1).toUpperCase())

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
