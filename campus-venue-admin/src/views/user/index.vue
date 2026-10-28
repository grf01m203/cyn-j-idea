<template>
  <div class="user-page">
    <page-header
      title="用户管理"
      icon="User"
      description="GET /api/v1/admin/users · PUT /api/v1/admin/users/{id}/status · PUT /api/v1/admin/users/{id}/credit"
    />

    <div class="page-container">
      <el-card shadow="never">
        <div class="search-bar">
          <el-input
            v-model="query.keyword"
            placeholder="用户名 / 昵称 / 学号 / 手机号"
            clearable
            style="width: 280px"
            @keyup.enter="onSearch"
            @clear="onSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
        </div>

        <div class="table-toolbar">
          <span class="text-muted">共 {{ total }} 个用户</span>
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        </div>

        <data-table
          :data="list"
          :loading="loading"
          :total="total"
          :page-num="query.pageNum"
          :page-size="query.pageSize"
          empty-text="暂无用户数据"
          @update:page-num="(v) => (query.pageNum = v)"
          @update:page-size="(v) => (query.pageSize = v)"
          @pagination-change="loadList"
        >
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="username" label="用户名" width="130" show-overflow-tooltip />
          <el-table-column prop="nickname" label="昵称" width="120" show-overflow-tooltip />
          <el-table-column prop="studentNo" label="学号/工号" width="130" show-overflow-tooltip />
          <el-table-column prop="phone" label="手机号" width="130" />
          <el-table-column label="角色" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="roleTagType(row.role)">{{ roleLabel(row.role) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="信用分" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="creditType(row.creditScore)">{{ row.creditScore ?? '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="violateCount" label="违约次数" width="100" align="center" />
          <el-table-column label="黑名单" width="130">
            <template #default="{ row }">
              <span v-if="isBlacklisted(row.blacklistUntil)" class="text-danger">
                {{ dateTime(row.blacklistUntil) }}
              </span>
              <span v-else class="text-muted">无</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="userStatusType(row.status)">{{ userStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="注册时间" width="170" show-overflow-tooltip />
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                :type="Number(row.status) === 1 ? 'danger' : 'success'"
                @click="onToggleStatus(row)"
              >
                {{ Number(row.status) === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button link type="primary" @click="openCredit(row)">调整信用分</el-button>
            </template>
          </el-table-column>
        </data-table>
      </el-card>
    </div>

    <el-dialog v-model="creditVisible" title="调整信用分" width="480px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="契约说明：score 为「调整后的目标分值」，不是增量；reason 为变动原因，会写入信用分流水。"
        class="mb-8"
      />
      <el-form ref="creditFormRef" :model="creditForm" :rules="creditRules" label-width="110px">
        <el-form-item label="用户">
          <span>{{ creditForm.nickname || creditForm.username }}（当前 {{ creditForm.currentScore }} 分）</span>
        </el-form-item>
        <el-form-item label="目标分值" prop="score">
          <el-input-number v-model="creditForm.score" :min="0" :max="100" :step="5" />
          <span class="text-muted ml-8">范围 0~100，初始 100</span>
        </el-form-item>
        <el-form-item label="调整原因" prop="reason">
          <el-input
            v-model="creditForm.reason"
            type="textarea"
            :rows="3"
            maxlength="100"
            show-word-limit
            placeholder="如 迟到未签到扣分 / 管理员手工恢复"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="creditVisible = false">取消</el-button>
        <el-button type="primary" :loading="creditSubmitting" @click="onSubmitCredit">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import DataTable from '@/components/DataTable.vue'
import { fetchUsers, updateUserStatus, updateUserCredit } from '@/api/user'
import { roleLabel, userStatusLabel, userStatusType } from '@/utils/format'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ keyword: '', pageNum: 1, pageSize: 10 })

const creditVisible = ref(false)
const creditSubmitting = ref(false)
const creditFormRef = ref(null)
const creditForm = reactive({
  id: null,
  username: '',
  nickname: '',
  currentScore: 100,
  score: 100,
  reason: ''
})

const creditRules = {
  score: [
    { required: true, message: '请输入目标分值', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        if (value === null || value === undefined) return callback(new Error('请输入目标分值'))
        if (value < 0 || value > 100) return callback(new Error('分值需在 0~100 之间'))
        callback()
      },
      trigger: 'change'
    }
  ],
  reason: [
    { required: true, message: '请输入调整原因', trigger: 'blur' },
    { min: 2, max: 100, message: '原因长度 2~100 个字符', trigger: 'blur' }
  ]
}

function roleTagType (role) {
  if (role === 'SYS_ADMIN') return 'danger'
  if (role === 'VENUE_ADMIN') return 'primary'
  return 'info'
}

function creditType (score) {
  const n = Number(score)
  if (!Number.isFinite(n)) return 'info'
  if (n >= 90) return 'success'
  if (n >= 60) return 'warning'
  return 'danger'
}

function dateTime (value) {
  if (!value) return '-'
  return String(value).replace('T', ' ')
}

function isBlacklisted (value) {
  if (!value) return false
  const t = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(t.getTime())) return true
  return t.getTime() > Date.now()
}

async function loadList () {
  loading.value = true
  try {
    const page = await fetchUsers({
      keyword: query.keyword || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = page?.records || []
    total.value = Number(page?.total ?? list.value.length)
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch () {
  query.pageNum = 1
  loadList()
}

function onReset () {
  query.keyword = ''
  query.pageNum = 1
  loadList()
}

async function onToggleStatus (row) {
  const target = Number(row.status) === 1 ? 0 : 1
  const actionText = target === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确认${actionText}用户「${row.nickname || row.username}」？${target === 0 ? '禁用后该用户将无法登录与预约。' : ''}`,
      `${actionText}确认`,
      { type: 'warning', confirmButtonText: `确认${actionText}`, cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await updateUserStatus(row.id, target)
    ElMessage.success(`用户已${actionText}`)
    loadList()
  } catch (e) {
    // 请求层已提示
  }
}

function openCredit (row) {
  creditForm.id = row.id
  creditForm.username = row.username
  creditForm.nickname = row.nickname
  creditForm.currentScore = Number(row.creditScore ?? 100)
  creditForm.score = Number(row.creditScore ?? 100)
  creditForm.reason = ''
  creditVisible.value = true
}

async function onSubmitCredit () {
  try {
    await creditFormRef.value.validate()
  } catch (e) {
    return
  }
  creditSubmitting.value = true
  try {
    await updateUserCredit(creditForm.id, {
      score: Number(creditForm.score),
      reason: creditForm.reason.trim()
    })
    ElMessage.success('信用分已调整')
    creditVisible.value = false
    loadList()
  } catch (e) {
    // 请求层已提示
  } finally {
    creditSubmitting.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.text-danger {
  color: #f56c6c;
}

.ml-8 {
  margin-left: 8px;
}
</style>
