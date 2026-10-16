<template>
  <div class="queue-page">
    <page-header
      title="候补监控"
      icon="AlarmClock"
      description="GET /api/v1/queues/my（契约中唯一的候补查询接口，仅返回当前登录人自己的候补记录）"
    />

    <div class="page-container">
      <el-alert type="error" :closable="false" show-icon title="接口缺失说明（不自行造接口）" class="mb-8">
        <template #default>
          docs/API.md 中候补模块只有 4 个接口：<code>POST /queues</code>（学生入队）、
          <code>GET /queues/my</code>（我的候补）、<code>POST /queues/{id}/confirm</code>、
          <code>POST /queues/{id}/cancel</code>，<strong>没有管理员候补全量列表接口</strong>。
          因此本页：① 仅调用 <code>GET /api/v1/queues/my</code> 展示当前登录管理员的候补记录（管理员账号通常为空）；
          ② 通过「场地时段占用监控」间接观察候补触发的时段释放情况。需要全量候补列表时，必须由后端补充管理端接口后再接入。
        </template>
      </el-alert>

      <el-card shadow="never" class="page-card">
        <template #header>
          <div class="card-header">
            <span>我的候补记录（GET /queues/my）</span>
            <el-button :icon="Refresh" size="small" :loading="myLoading" @click="loadMyQueues">刷新</el-button>
          </div>
        </template>
        <el-table :data="myQueues" v-loading="myLoading" border stripe size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="courtName" label="场地" min-width="150" show-overflow-tooltip />
          <el-table-column label="日期" width="120">
            <template #default="{ row }">{{ date(row.resvDate) }}</template>
          </el-table-column>
          <el-table-column label="时段" width="160">
            <template #default="{ row }">{{ hm(row.startTime) }} ~ {{ hm(row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="queueStatusType(row.status)">{{ queueStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="position" label="排位" width="90" align="center" />
          <el-table-column prop="expireTime" label="确认截止时间" width="180" show-overflow-tooltip />
          <template #empty>
            <el-empty description="当前登录账号没有候补记录（管理员账号通常为空，属预期现象）" :image-size="80" />
          </template>
        </el-table>
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
              图例：绿色空闲（0）、红色已占用（1）、灰色不可用（2）。时段释放后（取消/过期）候补将自动递补，候补人会在 10 分钟内确认。
            </div>
          </template>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { fetchMyQueues } from '@/api/queue'
import { fetchCourtSlots } from '@/api/venue'
import { useCourtOptions } from '@/composables/useCourtOptions'
import { queueStatusLabel, queueStatusType, slotNoToLabel, toDate as date, toHm as hm, today } from '@/utils/format'

const { courtOptions, loading: courtOptionsLoading, load: loadCourtOptions } = useCourtOptions()

const myLoading = ref(false)
const myQueues = ref([])

const slotLoading = ref(false)
const slotData = ref(null)
const slotForm = reactive({ courtId: null, date: today() })

const slotStat = computed(() => {
  const slots = slotData.value?.slots || []
  return {
    free: slots.filter((s) => Number(s.status) === 0).length,
    used: slots.filter((s) => Number(s.status) === 1).length,
    disabled: slots.filter((s) => Number(s.status) === 2).length
  }
})

async function loadMyQueues () {
  myLoading.value = true
  try {
    const data = await fetchMyQueues()
    myQueues.value = Array.isArray(data) ? data : []
  } catch (e) {
    myQueues.value = []
  } finally {
    myLoading.value = false
  }
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
  loadMyQueues()
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
</style>
