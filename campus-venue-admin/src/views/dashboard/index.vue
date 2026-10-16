<template>
  <div class="dashboard">
    <page-header title="数据看板" icon="DataLine" description="数据来源：/api/v1/admin/stat/overview、/trend、/heatmap">
      <template #extra>
        <el-button :icon="Refresh" :loading="loadingAll" @click="refreshAll">刷新</el-button>
      </template>
    </page-header>

    <div class="page-container">
      <!-- 概览卡片 -->
      <stat-card :items="overviewCards" :loading="loadingOverview" />

      <el-row :gutter="16">
        <!-- 趋势折线图 -->
        <el-col :xs="24" :lg="14">
          <el-card shadow="never" class="chart-card">
            <template #header>
              <div class="card-header">
                <span>预约趋势</span>
                <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
                  <el-radio-button :value="7">近 7 天</el-radio-button>
                  <el-radio-button :value="14">近 14 天</el-radio-button>
                  <el-radio-button :value="30">近 30 天</el-radio-button>
                </el-radio-group>
              </div>
            </template>
            <div v-loading="loadingTrend" class="chart-wrap">
              <div ref="trendRef" class="chart"></div>
              <el-empty v-if="!loadingTrend && !trendData.length" description="暂无趋势数据" :image-size="80" class="chart-empty" />
            </div>
          </el-card>
        </el-col>

        <!-- 取消率 / 签到率 -->
        <el-col :xs="24" :lg="10">
          <el-card shadow="never" class="chart-card">
            <template #header>
              <div class="card-header"><span>取消率 / 签到率</span></div>
            </template>
            <div v-loading="loadingOverview" class="chart-wrap">
              <div ref="rateRef" class="chart"></div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 热力图 -->
      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="card-header">
            <span>时段热度热力图（weekday × slotNo，颜色越深预约率越高）</span>
            <div class="header-filters">
              <el-date-picker
                v-model="heatRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                size="small"
                :clearable="true"
              />
              <el-button size="small" :icon="Refresh" :loading="loadingHeatmap" @click="loadHeatmap">查询</el-button>
            </div>
          </div>
        </template>
        <div v-loading="loadingHeatmap" class="chart-wrap chart-wrap--tall">
          <div ref="heatmapRef" class="chart"></div>
          <el-empty
            v-if="!loadingHeatmap && !heatmapData.length"
            description="暂无热力图数据"
            :image-size="90"
            class="chart-empty"
          />
        </div>
        <div class="text-muted mt-8">
          说明：热力图接口契约中 <code>label</code> 已由后端给出（形如 "15:00"）；若后端未返回 label，
          前端按 <code>slotNo/2=小时</code> 规则自行换算展示。
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { fetchOverview, fetchTrend, fetchHeatmap } from '@/api/stat'
import { WEEKDAY_LABELS, slotNoToLabel, toPercent } from '@/utils/format'

const loadingOverview = ref(false)
const loadingTrend = ref(false)
const loadingHeatmap = ref(false)
const overview = ref({})
const trendData = ref([])
const heatmapData = ref([])
const trendDays = ref(7)
const heatRange = ref([])

const loadingAll = computed(() => loadingOverview.value || loadingTrend.value || loadingHeatmap.value)

const overviewCards = computed(() => {
  const o = overview.value || {}
  return [
    { key: 'userCount', label: '用户数', value: num(o.userCount), icon: 'User', color: '#1f6feb' },
    { key: 'venueCount', label: '场馆数', value: num(o.venueCount), icon: 'OfficeBuilding', color: '#67c23a' },
    { key: 'courtCount', label: '场地数', value: num(o.courtCount), icon: 'Grid', color: '#e6a23c' },
    { key: 'resvToday', label: '今日预约', value: num(o.resvToday), icon: 'Calendar', color: '#909399' },
    { key: 'resvTotal', label: '累计预约', value: num(o.resvTotal), icon: 'Tickets', color: '#409eff' },
    { key: 'cancelRate', label: '取消率', value: toPercent(o.cancelRate), icon: 'CircleClose', color: '#f56c6c' },
    { key: 'signRate', label: '签到率', value: toPercent(o.signRate), icon: 'CircleCheck', color: '#67c23a' }
  ]
})

function num (v) {
  return v === null || v === undefined || v === '' ? '-' : String(v)
}

/* ---------------- ECharts 实例管理 ---------------- */
const trendRef = ref(null)
const rateRef = ref(null)
const heatmapRef = ref(null)
let trendChart = null
let rateChart = null
let heatmapChart = null

function ensureChart (el, instance) {
  if (!el) return null
  if (!instance || instance.isDisposed()) return echarts.init(el)
  return instance
}

function resizeAll () {
  ;[trendChart, rateChart, heatmapChart].forEach((c) => c && !c.isDisposed() && c.resize())
}

/* ---------------- 趋势 ---------------- */
function renderTrend () {
  if (!trendData.value.length) {
    if (trendChart && !trendChart.isDisposed()) trendChart.clear()
    return
  }
  trendChart = ensureChart(trendRef.value, trendChart)
  if (!trendChart) return
  const dates = trendData.value.map((it) => it.date)
  trendChart.setOption(
    {
      tooltip: { trigger: 'axis' },
      legend: { data: ['预约数', '取消数', '签到数'], right: 10, top: 0 },
      grid: { left: 40, right: 20, bottom: 30, top: 40 },
      xAxis: { type: 'category', boundaryGap: false, data: dates, axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          name: '预约数',
          type: 'line',
          smooth: true,
          data: trendData.value.map((it) => it.resvCount ?? 0),
          itemStyle: { color: '#1f6feb' },
          areaStyle: { opacity: 0.12 }
        },
        {
          name: '取消数',
          type: 'line',
          smooth: true,
          data: trendData.value.map((it) => it.cancelCount ?? 0),
          itemStyle: { color: '#f56c6c' }
        },
        {
          name: '签到数',
          type: 'line',
          smooth: true,
          data: trendData.value.map((it) => it.signCount ?? 0),
          itemStyle: { color: '#67c23a' }
        }
      ]
    },
    true
  )
}

/* ---------------- 取消率/签到率 ---------------- */
function renderRate () {
  rateChart = ensureChart(rateRef.value, rateChart)
  if (!rateChart) return
  const o = overview.value || {}
  const cancelRate = normalizeRate(o.cancelRate)
  const signRate = normalizeRate(o.signRate)
  rateChart.setOption(
    {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (v) => `${v}%` },
      grid: { left: 60, right: 30, bottom: 20, top: 20 },
      xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      yAxis: { type: 'category', data: ['取消率', '签到率'] },
      series: [
        {
          type: 'bar',
          barWidth: 22,
          label: { show: true, position: 'right', formatter: '{c}%' },
          data: [
            { value: cancelRate, itemStyle: { color: '#f56c6c' } },
            { value: signRate, itemStyle: { color: '#67c23a' } }
          ]
        }
      ]
    },
    true
  )
}

/** 契约中 rate 为 0~1；兼容后端直接返回百分数的情况 */
function normalizeRate (v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return 0
  return Number((n <= 1 ? n * 100 : n).toFixed(1))
}

/* ---------------- 热力图 ---------------- */
function renderHeatmap () {
  if (!heatmapData.value.length) {
    if (heatmapChart && !heatmapChart.isDisposed()) heatmapChart.clear()
    return
  }
  heatmapChart = ensureChart(heatmapRef.value, heatmapChart)
  if (!heatmapChart) return

  const weekdays = [1, 2, 3, 4, 5, 6, 7]
  const slots = [...new Set(heatmapData.value.map((it) => Number(it.slotNo)))].sort((a, b) => a - b)
  const slotIndex = new Map(slots.map((s, i) => [s, i]))
  const points = heatmapData.value.map((it) => [
    slotIndex.get(Number(it.slotNo)),
    Number(it.weekday) - 1,
    Number(it.rate ?? 0)
  ])
  const maxRate = Math.max(0.01, ...heatmapData.value.map((it) => Number(it.rate ?? 0)))

  heatmapChart.setOption(
    {
      tooltip: {
        position: 'top',
        formatter: (p) => {
          const wd = WEEKDAY_LABELS[p.value[1]] || `周${p.value[1] + 1}`
          const slot = slots[p.value[0]]
          const raw = heatmapData.value.find(
            (it) => Number(it.slotNo) === Number(slot) && Number(it.weekday) === p.value[1] + 1
          )
          const label = raw?.label || slotNoToLabel(slot)
          return `${wd} ${label}<br/>预约率：${toPercent(p.value[2], 1)}`
        }
      },
      grid: { left: 60, right: 20, bottom: 70, top: 20 },
      xAxis: {
        type: 'category',
        data: weekdays.map((w) => WEEKDAY_LABELS[w - 1]),
        splitArea: { show: true }
      },
      yAxis: {
        type: 'category',
        data: slots.map((s) => {
          const hit = heatmapData.value.find((it) => Number(it.slotNo) === s && it.label)
          return hit?.label || slotNoToLabel(s)
        }),
        splitArea: { show: true },
        axisLabel: { fontSize: 11 }
      },
      visualMap: {
        min: 0,
        max: maxRate,
        calculable: true,
        orient: 'horizontal',
        left: 'center',
        bottom: 10,
        formatter: (v) => `${Math.round(v * 100)}%`,
        inRange: { color: ['#e8f3ff', '#a3ccff', '#4f9bf5', '#1f6feb', '#0b3d91'] }
      },
      series: [
        {
          name: '预约率',
          type: 'heatmap',
          data: points,
          label: { show: slots.length <= 12, fontSize: 10, formatter: (p) => `${Math.round(p.value[2] * 100)}%` },
          emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.4)' } }
        }
      ]
    },
    true
  )
}

/* ---------------- 数据加载 ---------------- */
async function loadOverview () {
  loadingOverview.value = true
  try {
    overview.value = (await fetchOverview()) || {}
  } catch (e) {
    overview.value = {}
  } finally {
    loadingOverview.value = false
  }
  await nextTick()
  renderRate()
}

async function loadTrend () {
  loadingTrend.value = true
  try {
    const data = await fetchTrend(trendDays.value)
    trendData.value = Array.isArray(data) ? data : []
  } catch (e) {
    trendData.value = []
  } finally {
    loadingTrend.value = false
  }
  await nextTick()
  renderTrend()
}

async function loadHeatmap () {
  loadingHeatmap.value = true
  try {
    const params = {}
    if (heatRange.value?.length === 2) {
      params.startDate = heatRange.value[0]
      params.endDate = heatRange.value[1]
    }
    const data = await fetchHeatmap(params)
    heatmapData.value = Array.isArray(data) ? data : []
  } catch (e) {
    heatmapData.value = []
  } finally {
    loadingHeatmap.value = false
  }
  await nextTick()
  renderHeatmap()
}

async function refreshAll () {
  await Promise.all([loadOverview(), loadTrend(), loadHeatmap()])
}

watch(trendDays, () => loadTrend())

let resizeTimer = null
function onResize () {
  window.clearTimeout(resizeTimer)
  resizeTimer = window.setTimeout(resizeAll, 150)
}

onMounted(async () => {
  await refreshAll()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  ;[trendChart, rateChart, heatmapChart].forEach((c) => c && !c.isDisposed() && c.dispose())
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 14px;
  font-weight: 600;
}

.header-filters {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chart-card {
  margin-bottom: 16px;
}

.chart-wrap {
  position: relative;
  height: 300px;
}

.chart-wrap--tall {
  height: 420px;
}

.chart {
  width: 100%;
  height: 100%;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
</style>
