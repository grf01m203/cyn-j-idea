<template>
  <div class="reservation-page">
    <page-header
      title="预约管理"
      icon="Tickets"
      description="GET /api/v1/admin/reservations?status=&date=&pageNum=&pageSize= · 强制取消 POST /api/v1/reservations/{id}/cancel"
    />

    <div class="page-container">
      <el-card shadow="never">
        <div class="search-bar">
          <el-select v-model="query.status" placeholder="预约状态" clearable style="width: 150px" @change="onSearch">
            <el-option v-for="(item, key) in RESERVATION_STATUS" :key="key" :label="item.label" :value="Number(key)" />
          </el-select>
          <el-date-picker
            v-model="query.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="预约日期"
            clearable
            style="width: 180px"
            @change="onSearch"
          />
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
        </div>

        <div class="table-toolbar">
          <span class="text-muted">共 {{ total }} 条预约记录</span>
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        </div>

        <data-table
          :data="list"
          :loading="loading"
          :total="total"
          :page-num="query.pageNum"
          :page-size="query.pageSize"
          empty-text="暂无符合条件的预约记录"
          @update:page-num="(v) => (query.pageNum = v)"
          @update:page-size="(v) => (query.pageSize = v)"
          @pagination-change="loadList"
        >
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="resvNo" label="预约单号" width="180" show-overflow-tooltip />
          <el-table-column prop="userName" label="预约人" width="120" show-overflow-tooltip />
          <el-table-column prop="courtName" label="场地" min-width="150" show-overflow-tooltip />
          <el-table-column label="预约日期" width="120">
            <template #default="{ row }">{{ date(row.resvDate) }}</template>
          </el-table-column>
          <el-table-column label="时段" width="160">
            <template #default="{ row }">{{ hm(row.startTime) }} ~ {{ hm(row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="reservationStatusType(row.status)">
                {{ reservationStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">￥{{ money(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170" show-overflow-tooltip />
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button
                link
                type="danger"
                :disabled="!canCancel(row)"
                @click="openCancel(row)"
              >
                强制取消
              </el-button>
            </template>
          </el-table-column>
        </data-table>
      </el-card>
    </div>

    <!-- 取消弹窗 -->
    <el-dialog v-model="cancelVisible" title="管理员强制取消预约" width="480px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="强制取消会释放时段并按后端规则计算违约金 / 信用分影响，请谨慎操作。"
        class="mb-8"
      />
      <el-form :model="cancelForm" label-width="90px">
        <el-form-item label="预约单号">
          <span>{{ cancelForm.resvNo || '-' }}</span>
        </el-form-item>
        <el-form-item label="取消原因" required>
          <el-input
            v-model="cancelForm.reason"
            type="textarea"
            :rows="3"
            maxlength="100"
            show-word-limit
            placeholder="请填写取消原因（必填，将记录在预约单中）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelVisible = false">取消</el-button>
        <el-button type="danger" :loading="cancelling" @click="onCancel">确认取消预约</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="预约详情" width="620px">
      <div v-loading="detailLoading">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="ID">{{ detail.id ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="预约单号">{{ detail.resvNo ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="预约人">{{ detail.userName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="场地">{{ detail.courtName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="预约日期">{{ date(detail.resvDate) }}</el-descriptions-item>
          <el-descriptions-item label="时段">
            {{ hm(detail.startTime) }} ~ {{ hm(detail.endTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="reservationStatusType(detail.status)">
              {{ reservationStatusLabel(detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="金额">￥{{ money(detail.amount) }}</el-descriptions-item>
          <el-descriptions-item label="来源">
            {{ Number(detail.source) === 2 ? '拼场' : Number(detail.source) === 1 ? '个人预约' : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="拼场 ID">{{ detail.teamId ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="members.length" class="mt-8">
          <div class="text-muted mb-8">参与成员</div>
          <el-tag v-for="m in members" :key="m.userId" class="member-tag" type="info">
            {{ m.nickname || m.userId }}
          </el-tag>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import DataTable from '@/components/DataTable.vue'
import { fetchAdminReservations, fetchReservation, cancelReservation } from '@/api/reservation'
import {
  RESERVATION_STATUS,
  reservationStatusLabel,
  reservationStatusType,
  toDate as date,
  toHm as hm,
  toMoney as money
} from '@/utils/format'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ status: null, date: '', pageNum: 1, pageSize: 10 })

const cancelVisible = ref(false)
const cancelling = ref(false)
const cancelForm = reactive({ id: null, resvNo: '', reason: '' })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref({})
const members = ref([])

/** 可取消状态：待支付 0 / 已确认 1 / 已签到 2（已取消 5、已完成 3、未签到 4、过期 6/7 不再允许） */
const CANCELABLE = [0, 1, 2]

function canCancel (row) {
  return CANCELABLE.includes(Number(row.status))
}

async function loadList () {
  loading.value = true
  try {
    const page = await fetchAdminReservations({
      status: query.status === null || query.status === '' ? undefined : query.status,
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
  query.date = ''
  query.pageNum = 1
  loadList()
}

function openCancel (row) {
  cancelForm.id = row.id
  cancelForm.resvNo = row.resvNo
  cancelForm.reason = ''
  cancelVisible.value = true
}

async function onCancel () {
  if (!cancelForm.reason.trim()) {
    ElMessage.warning('请填写取消原因')
    return
  }
  cancelling.value = true
  try {
    const res = await cancelReservation(cancelForm.id, cancelForm.reason.trim())
    const penalty = res?.penalty
    ElMessage.success(
      penalty === null || penalty === undefined
        ? '预约已强制取消'
        : `预约已强制取消，违约金 ￥${Number(penalty).toFixed(2)}`
    )
    cancelVisible.value = false
    loadList()
  } catch (e) {
    // 请求层已提示
  } finally {
    cancelling.value = false
  }
}

async function openDetail (row) {
  detailVisible.value = true
  detail.value = { ...row }
  members.value = []
  // 管理端列表契约未定义成员字段，尝试用通用详情接口补充；失败则仅展示列表字段
  detailLoading.value = true
  try {
    const data = await fetchReservation(row.id)
    if (data && typeof data === 'object') {
      detail.value = { ...row, ...data }
      members.value = Array.isArray(data.members) ? data.members : []
    }
  } catch (e) {
    // 详情接口可能无权限/未实现，静默降级
  } finally {
    detailLoading.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.member-tag {
  margin: 0 6px 6px 0;
}
</style>
