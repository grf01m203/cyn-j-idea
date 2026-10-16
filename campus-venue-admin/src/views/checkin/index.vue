<template>
  <div class="checkin-page">
    <page-header
      title="签到记录"
      icon="Checked"
      description="GET /api/v1/checkin/records?reservationId=（支持输入预约单号或预约 ID 查询）"
    />

    <div class="page-container">
      <el-card shadow="never">
        <div class="search-bar">
          <el-input
            v-model="reservationId"
            placeholder="请输入预约 ID（数字）"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-input
            v-model="resvNo"
            placeholder="或输入预约单号定位预约 ID"
            clearable
            style="width: 260px"
            @keyup.enter="onSearchByNo"
          >
            <template #prefix><el-icon><Tickets /></el-icon></template>
          </el-input>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
        </div>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb-8"
          title="契约说明：签到记录接口以 reservationId 为查询参数，返回该预约对应的签到记录数组；按预约单号查询时先通过 GET /api/v1/reservations/{id} 校验，或直接使用管理端预约列表中的 ID。"
        />

        <div class="table-toolbar">
          <span class="text-muted">
            {{ reservationId ? `预约 ID：${reservationId}` : '未指定预约 ID' }}，共 {{ list.length }} 条签到记录
          </span>
          <el-button :icon="Refresh" :disabled="!reservationId" @click="loadRecords">刷新</el-button>
        </div>

        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="reservationId" label="预约 ID" width="110" align="center" />
          <el-table-column prop="userName" label="签到人" min-width="140" show-overflow-tooltip />
          <el-table-column prop="checkinTime" label="签到时间" min-width="180" show-overflow-tooltip />
          <el-table-column label="渠道" width="130">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ channelLabel(row.channel) }}</el-tag>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty
              :description="reservationId ? '该预约暂无签到记录' : '请输入预约 ID 后查询'"
              :image-size="90"
            />
          </template>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshLeft, Search, Tickets } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { fetchCheckinRecords } from '@/api/checkin'
import { fetchAdminReservations } from '@/api/reservation'

const route = useRoute()

const reservationId = ref('')
const resvNo = ref('')
const loading = ref(false)
const list = ref([])

const CHANNEL_LABELS = {
  QRCODE: '扫码签到',
  ADMIN: '管理员代签',
  MANUAL: '手工补签'
}

function channelLabel (channel) {
  return CHANNEL_LABELS[channel] || channel || '-'
}

async function loadRecords () {
  if (!reservationId.value) {
    list.value = []
    return
  }
  loading.value = true
  try {
    const data = await fetchCheckinRecords(reservationId.value)
    list.value = Array.isArray(data) ? data : data?.records || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function onSearch () {
  const val = String(reservationId.value || '').trim()
  if (!val) {
    ElMessage.warning('请输入预约 ID')
    return
  }
  if (!/^\d+$/.test(val)) {
    ElMessage.warning('预约 ID 必须是数字，预约单号请使用右侧输入框')
    return
  }
  reservationId.value = val
  loadRecords()
}

/** 用预约单号在管理端预约列表中定位 ID（契约无按单号查询签到记录的能力） */
async function onSearchByNo () {
  const no = String(resvNo.value || '').trim()
  if (!no) {
    ElMessage.warning('请输入预约单号')
    return
  }
  loading.value = true
  try {
    const page = await fetchAdminReservations({ pageNum: 1, pageSize: 50 })
    const records = page?.records || []
    const hit = records.find((r) => String(r.resvNo) === no)
    if (!hit) {
      ElMessage.warning('在当前页预约列表中未找到该预约单号，请先在「预约管理」中确认')
      return
    }
    reservationId.value = String(hit.id)
    loadRecords()
  } catch (e) {
    // 请求层已提示
  } finally {
    loading.value = false
  }
}

function onReset () {
  reservationId.value = ''
  resvNo.value = ''
  list.value = []
}

onMounted(() => {
  const fromQuery = route.query.reservationId
  if (fromQuery) {
    reservationId.value = String(fromQuery)
    loadRecords()
  }
})
</script>
