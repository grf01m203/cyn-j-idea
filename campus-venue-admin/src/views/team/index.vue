<template>
  <div class="team-page">
    <page-header
      title="拼场监控"
      icon="UserFilled"
      description="GET /api/v1/teams?courtId=&date=（契约中唯一的团队列表接口，非管理端专用）· 详情 GET /api/v1/teams/{id}"
    />

    <div class="page-container">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="契约说明"
        class="mb-8"
      >
        <template #default>
          docs/API.md 中没有「管理员拼场列表」接口，本页复用学生端公开接口
          <code>GET /api/v1/teams?courtId=&amp;date=</code>，因此<strong>不传 courtId/date 时后端可能返回空列表</strong>；
          请至少选择一个场地或日期进行监控。团队成员明细需逐个调用 <code>GET /api/v1/teams/{id}</code>。
        </template>
      </el-alert>

      <el-card shadow="never">
        <div class="search-bar">
          <el-select
            v-model="query.courtId"
            filterable
            clearable
            placeholder="选择场地（必选于后端过滤）"
            style="width: 280px"
            :loading="courtOptionsLoading"
          >
            <el-option v-for="opt in courtOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-date-picker
            v-model="query.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="拼场日期"
            clearable
            style="width: 180px"
          />
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        </div>

        <div class="table-toolbar">
          <span class="text-muted">共 {{ list.length }} 个拼场</span>
          <span class="text-muted">状态字典：0 招募中 / 1 人数已满 / 2 已成团 / 3 已解散</span>
        </div>

        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="creatorName" label="发起人" width="120" show-overflow-tooltip />
          <el-table-column prop="courtName" label="场地" min-width="150" show-overflow-tooltip />
          <el-table-column label="日期" width="120">
            <template #default="{ row }">{{ date(row.resvDate) }}</template>
          </el-table-column>
          <el-table-column label="时段" width="160">
            <template #default="{ row }">{{ hm(row.startTime) }} ~ {{ hm(row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="人数" width="110" align="center">
            <template #default="{ row }">
              {{ row.currentMembers ?? '-' }} / {{ row.maxMembers ?? '-' }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="teamStatusType(row.status)">{{ teamStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">成员</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无拼场数据（请指定场地或日期后重试）" :image-size="90" />
          </template>
        </el-table>
      </el-card>
    </div>

    <el-dialog v-model="detailVisible" :title="`拼场成员 · 队伍 #${detail.id ?? ''}`" width="620px">
      <div v-loading="detailLoading">
        <el-descriptions :column="2" border size="small" class="mb-8">
          <el-descriptions-item label="发起人">{{ detail.creatorName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="场地">{{ detail.courtName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="日期">{{ date(detail.resvDate) }}</el-descriptions-item>
          <el-descriptions-item label="时段">
            {{ hm(detail.startTime) }} ~ {{ hm(detail.endTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="teamStatusType(detail.status)">{{ teamStatusLabel(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="人数">
            {{ detail.currentMembers ?? '-' }} / {{ detail.maxMembers ?? '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-table :data="members" border size="small" empty-text="暂无成员数据">
          <el-table-column prop="id" label="成员ID" width="90" />
          <el-table-column prop="userId" label="用户ID" width="90" />
          <el-table-column prop="nickname" label="昵称" min-width="120" />
          <el-table-column label="成员状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="memberStatusType(row.status)">{{ memberStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="队长" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="Number(row.isLeader) === 1" size="small" type="warning">队长</el-tag>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { fetchTeams, fetchTeam } from '@/api/team'
import { useCourtOptions } from '@/composables/useCourtOptions'
import { teamStatusLabel, teamStatusType, toDate as date, toHm as hm } from '@/utils/format'

const { courtOptions, loading: courtOptionsLoading, load: loadCourtOptions } = useCourtOptions()

const loading = ref(false)
const list = ref([])
const query = reactive({ courtId: null, date: '' })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref({})
const members = ref([])

const MEMBER_STATUS = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已拒绝', type: 'danger' },
  3: { label: '已退出', type: 'info' }
}

function memberStatusLabel (status) {
  return MEMBER_STATUS[status]?.label ?? `未知(${status})`
}

function memberStatusType (status) {
  return MEMBER_STATUS[status]?.type ?? 'info'
}

async function loadList () {
  if (!query.courtId && !query.date) {
    // 契约要求 courtId/date 过滤，两者皆空时不发请求，避免拿到全量或空数据造成误解
    list.value = []
    return
  }
  loading.value = true
  try {
    const data = await fetchTeams({
      courtId: query.courtId || undefined,
      date: query.date || undefined
    })
    list.value = Array.isArray(data) ? data : data?.records || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function onReset () {
  query.courtId = null
  query.date = ''
  list.value = []
}

async function openDetail (row) {
  detailVisible.value = true
  detail.value = { ...row }
  members.value = []
  detailLoading.value = true
  try {
    const data = await fetchTeam(row.id)
    if (data && typeof data === 'object') {
      detail.value = { ...row, ...data }
      members.value = Array.isArray(data.members) ? data.members : []
    }
  } catch (e) {
    // 静默降级：仅展示列表字段
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  loadCourtOptions()
  query.date = date(new Date())
  loadList()
})
</script>
