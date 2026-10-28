<template>
  <div class="queue-page">
    <page-header
      title="候补监控"
      icon="AlarmClock"
      description="管理端全局视图（GET /api/v1/admin/queues）· 时段占用 GET /api/v1/courts/{id}/slots?date="
    />

    <div class="page-container">
      <el-card shadow="never" class="page-card">
        <div class="search-bar">
          <el-select
            v-model="query.status"
            placeholder="候补状态"
            clearable
            style="width: 150px"
            @change="onSearch"
          >
            <el-option
              v-for="(item, key) in QUEUE_STATUS"
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
            placeholder="候补日期"
            clearable
            style="width: 170px"
            @change="onSearch"
          />
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        </div>

        <div class="table-toolbar">
          <span class="text-muted">共 {{ total }} 条候补记录</span>
          <span class="text-muted">状态字典：0 等待中 / 1 已通知 / 2 已确认 / 3 已过期 / 4 已取消</span>
        </div>

        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="id" label="ID" width="62" />
          <el-table-column prop="courtName" label="场地" min-width="140" show-overflow-tooltip />
          <el-table-column prop="userName" label="用户" width="95" show-overflow-tooltip />
          <el-table-column label="日期" width="105">
            <template #default="{ row }">{{ date(row.resvDate) }}</template>
          </el-table-column>
          <el-table-column label="时段" width="130">
            <template #default="{ row }">{{ hm(row.startTime) }} ~ {{ hm(row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="排位" width="65" align="center">
            <template #default="{ row }">
              <el-tag v-if="Number(row.status) === 0 && row.position != null" size="small" type="warning" effect="plain">
                {{ row.position }}
              </el-tag>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="88" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="queueStatusType(row.status)">{{ queueStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="expireTime" label="确认截止时间" min-width="150" show-overflow-tooltip />
          <el-table-column prop="reservationId" label="关联预约" width="85" align="center">
            <template #default="{ row }">{{ row.reservationId ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" min-width="150" show-overflow-tooltip />
          <template #empty>
            <el-empty description="暂无符合条件的候补记录" :image-size="90" />
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

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>场地时段占用监控（间接观察候补递补）</span>
            <div class="header-filters">
              <el-select
                v-model="slotForm.courtId"
                filterable
                clearable
                placeholder="选择场地"
                style="width: 260px"
                :loading="courtOptionsLoading"
              >
                <el-option v-for="opt in courtOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
              <el-date-picker
                v-model="slotForm.date"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="日期"
                style="width: 160px"
              />
              <el-button type="primary" size="small" :icon="Search" :loading="slotLoading" @click="loadSlots">
                查询占用
              </el-button>
            </div>
          </div>
        </template>

        <div v-loading="slotLoading">
          <el-empty
            v-if="!slotData"
            description="请选择场地与日期后查询时段占用（GET /api/v1/courts/{id}/slots?date=）"
            :image-size="90"
          />
          <template v-else>
            <div class="slot-summary">
              <span>{{ slotData.courtName || '-' }}</span>
              <el-tag size="small" type="info">日期 {{ slotData.date }}</el-tag>
              <el-tag size="small" type="success">空闲 {{ slotStat.free }}</el-tag>
              <el-tag size="small" type="danger">已占用 {{ slotStat.used }}</el-tag>
              <el-tag size="small" type="warning">不可用 {{ slotStat.disabled }}</el-tag>
              <el-tag size="small" type="info" effect="plain" class="slot-tag-past">
                已过期 {{ slotStat.expired }}
              </el-tag>
            </div>
            <div class="slot-grid">
              <div
                v-for="slot in slotData.slots || []"
                :key="slot.slotNo"
                class="slot-item"
                :class="`slot-item--${slot.status}`"
                :title="`${slot.label || slotNoToLabel(slot.slotNo)}（status=${slot.status}）`"
              >
                {{ slot.label || slotNoToLabel(slot.slotNo) }}
              </div>
            </div>
            <div class="text-muted mt-8">
              图例：绿色空闲、红色已占用、灰色不可用、深灰已过期（时间已过）。时段释放后（取消/过期）候补将自动递补，候补人会在 10 分钟内确认。
            </div>
          </template>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { fetchAdminQueues } from '@/api/queue'
import { fetchCourtSlots } from '@/api/venue'
import { useCourtOptions } from '@/composables/useCourtOptions'
import {
  QUEUE_STATUS,
  queueStatusLabel,
  queueStatusType,
  slotNoToLabel,
  toDate as date,
  toHm as hm,
  today
} from '@/utils/format'

const { courtOptions, loading: courtOptionsLoading, load: loadCourtOptions } = useCourtOptions()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ status: null, courtId: null, date: '', pageNum: 1, pageSize: 10 })

const slotLoading = ref(false)
const slotData = ref(null)
const slotForm = reactive({ courtId: null, date: today() })

const slotStat = computed(() => {
  const slots = slotData.value?.slots || []
  return {
    free: slots.filter((s) => Number(s.status) === 0).length,
    used: slots.filter((s) => Number(s.status) === 1).length,
    disabled: slots.filter((s) => Number(s.status) === 2).length,
    expired: slots.filter((s) => Number(s.status) === 3).length
  }
})

async function loadList () {
  loading.value = true
  try {
    const page = await fetchAdminQueues({
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

async function loadSlots () {
  if (!slotForm.courtId || !slotForm.date) return
  slotLoading.value = true
  try {
    slotData.value = await fetchCourtSlots(slotForm.courtId, slotForm.date)
  } catch (e) {
    slotData.value = null
  } finally {
    slotLoading.value = false
  }
}

onMounted(() => {
  loadCourtOptions()
  loadList()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-weight: 600;
}

.header-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.slot-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-weight: 600;
  flex-wrap: wrap;
}

.slot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(72px, 1fr));
  gap: 6px;
}

.slot-item {
  text-align: center;
  padding: 6px 0;
  border-radius: 4px;
  font-size: 12px;
  border: 1px solid transparent;
}

.slot-item--0 {
  background-color: #f0f9eb;
  color: #529b2e;
  border-color: #c2e7b0;
}

.slot-item--1 {
  background-color: #fef0f0;
  color: #c45656;
  border-color: #fbc4c4;
}

.slot-item--2 {
  background-color: #f4f4f5;
  color: #909399;
  border-color: #e9e9eb;
}

/* 已过期（时间已过）：深灰，与「不可用」区分 */
.slot-item--3 {
  background-color: #e9e9eb;
  color: #6b6b6b;
  border-color: #cfcfd4;
  text-decoration: line-through;
}

.slot-tag-past {
  background-color: #e9e9eb;
  border-color: #cfcfd4;
  color: #6b6b6b;
}
</style>
