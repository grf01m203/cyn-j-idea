<template>
  <div class="team-page">
    <page-header
      title="拼场监控"
      icon="UserFilled"
      description="管理端全局视图（GET /api/v1/admin/teams）"
    />

    <div class="page-container">
      <el-card shadow="never">
        <div class="search-bar">
          <el-select
            v-model="query.status"
            placeholder="拼场状态"
            clearable
            style="width: 150px"
            @change="onSearch"
          >
            <el-option
              v-for="(item, key) in TEAM_STATUS"
              :key="key"
              :label="item.label"
              :value="Number(key)"
            />
          </el-select>
          <el-select
            v-model="query.courtId"
            filterable
            clearable
            placeholder="选择场地"
            style="width: 260px"
            :loading="courtOptionsLoading"
            @change="onSearch"
          >
            <el-option v-for="opt in courtOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-date-picker
            v-model="query.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="拼场日期"
            clearable
            style="width: 170px"
            @change="onSearch"
          />
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        </div>

        <div class="table-toolbar">
          <span class="text-muted">共 {{ total }} 个拼场</span>
          <span class="text-muted">状态字典：0 招募中 / 1 已满 / 2 已成团 / 3 已解散</span>
        </div>

        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="courtName" label="场地" min-width="150" show-overflow-tooltip />
          <el-table-column prop="creatorName" label="发起人" width="120" show-overflow-tooltip />
          <el-table-column label="日期" width="115">
            <template #default="{ row }">{{ date(row.resvDate) }}</template>
          </el-table-column>
          <el-table-column label="时段" width="150">
            <template #default="{ row }">{{ hm(row.startTime) }} ~ {{ hm(row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="成员数" width="100" align="center">
            <template #default="{ row }">
              <span>{{ row.currentMembers ?? '-' }} / {{ row.maxMembers ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="105" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="teamStatusType(row.status)">{{ teamStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="expireTime" label="招募截止" width="175" show-overflow-tooltip />
          <el-table-column prop="createTime" label="创建时间" width="175" show-overflow-tooltip />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">成员</el-button>
              <el-button
                v-if="canDissolve(row)"
                link
                type="danger"
                :loading="dissolvingId === row.id"
                @click="onDissolve(row)"
              >
                解散
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无符合条件的拼场记录" :image-size="90" />
          </template>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            :current-page="query.pageNum"
            :page-size="query.pageSize"
            :page-sizes="[10, 20, 50]"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </div>
      </el-card>
    </div>

    <el-dialog v-model="detailVisible" :title="`拼场成员 · 队伍 #${detail.id ?? ''}`" width="680px">
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
          <el-descriptions-item label="成员数">
            {{ detail.currentMembers ?? '-' }} / {{ detail.maxMembers ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="关联预约">
            {{ detail.reservationId ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="招募截止">{{ detail.expireTime ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="members" border size="small" empty-text="暂无成员数据">
          <el-table-column prop="id" label="成员ID" width="85" />
          <el-table-column prop="userId" label="用户ID" width="85" />
          <el-table-column label="昵称" min-width="130">
            <template #default="{ row }">
              {{ row.nickname || '-' }}
              <el-tag v-if="isMe(row)" size="small" type="success" effect="plain">我</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="成员状态" width="105">
            <template #default="{ row }">
              <el-tag size="small" :type="memberStatusType(row.status)">{{ memberStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="队长" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="isLeader(row)" size="small" type="warning">队长</el-tag>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { fetchAdminTeams, fetchAdminTeam, dissolveAdminTeam } from '@/api/team'
import { useCourtOptions } from '@/composables/useCourtOptions'
import { TEAM_STATUS, teamStatusLabel, teamStatusType, toDate as date, toHm as hm } from '@/utils/format'

const { courtOptions, loading: courtOptionsLoading, load: loadCourtOptions } = useCourtOptions()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ status: null, courtId: null, date: '', pageNum: 1, pageSize: 10 })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref({})
const members = ref([])
const dissolvingId = ref(null)

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

/** 后端 isLeader / isMe 为布尔值（兼容 0/1） */
function isLeader (row) {
  return row?.isLeader === true || Number(row?.isLeader) === 1
}

function isMe (row) {
  return row?.isMe === true || Number(row?.isMe) === 1
}

/** 仅「招募中 / 已满」可解散；已成团须先取消预约，已解散无需操作 */
function canDissolve (row) {
  const status = Number(row?.status)
  return status === 0 || status === 1
}

async function loadList () {
  loading.value = true
  try {
    const page = await fetchAdminTeams({
      status: query.status === null || query.status === '' ? undefined : query.status,
      courtId: query.courtId || undefined,
      date: query.date || undefined,
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
  query.status = null
  query.courtId = null
  query.date = ''
  query.pageNum = 1
  loadList()
}

function onPageChange (page) {
  query.pageNum = page
  loadList()
}

function onSizeChange (size) {
  query.pageSize = size
  query.pageNum = 1
  loadList()
}

async function openDetail (row) {
  detailVisible.value = true
  detail.value = { ...row }
  members.value = []
  detailLoading.value = true
  try {
    const data = await fetchAdminTeam(row.id)
    if (data && typeof data === 'object') {
      detail.value = { ...row, ...data }
      members.value = Array.isArray(data.members) ? data.members : []
    }
  } catch (e) {
    // 请求层已提示；保留列表字段兜底
  } finally {
    detailLoading.value = false
  }
}

async function onDissolve (row) {
  try {
    await ElMessageBox.confirm(
      `确认强制解散拼场 #${row.id}（${row.courtName || '未知场地'}）？解散后所有成员会收到通知，且不可恢复。`,
      '解散确认',
      {
        type: 'warning',
        confirmButtonText: '确认解散',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch (e) {
    return
  }
  dissolvingId.value = row.id
  try {
    await dissolveAdminTeam(row.id)
    ElMessage.success('拼场已解散')
    if (detailVisible.value && detail.value.id === row.id) detailVisible.value = false
    loadList()
  } catch (e) {
    // 请求层已用 ElMessage 展示后端 message，例如：
    // 「该拼场已成团并生成预约单，请在预约管理中取消预约后再处理」（code=30009）
  } finally {
    dissolvingId.value = null
  }
}

onMounted(() => {
  loadCourtOptions()
  loadList()
})
</script>
