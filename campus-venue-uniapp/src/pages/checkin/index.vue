<template>
  <view class="page">
    <view class="page-body">
      <!-- 已确认预约：生成签到码 -->
      <view class="card">
        <view class="row-between">
          <text class="card-title">我的签到码</text>
          <text class="text-sm text-muted" v-if="current">{{ statusText(current.status) }}</text>
        </view>

        <StateView
          :status="pageState"
          loading-text="正在加载可签到预约…"
          :error-text="errorMsg"
          empty-text="暂无已确认的预约，无法生成签到码"
          empty-action-text="查看我的预约"
          :show-empty-action="true"
          @retry="initReservation"
          @action="goReservationList"
        >
          <view class="resv-pick" v-if="candidates.length > 1">
            <text class="text-sm text-muted">选择预约单（{{ current && current.resvNo }}）</text>
            <scroll-view scroll-x :show-scrollbar="false" class="pick-scroll">
              <view class="pick-row">
                <view
                  v-for="r in candidates"
                  :key="r.id"
                  class="pick-chip"
                  :class="{ active: current && r.id === current.id }"
                  @click="switchReservation(r)"
                >
                  <text class="pick-no">{{ r.courtName }}</text>
                  <text class="pick-time">{{ shortTime(r.startTime) }}</text>
                </view>
              </view>
            </scroll-view>
          </view>

          <view class="code-box" v-if="code">
            <text class="code-value" :class="{ expired: remainMs <= 0 }">{{ code }}</text>
            <text class="code-tip" v-if="remainMs > 0">
              剩余有效时间 <text class="text-primary text-bold">{{ countdownText(remainMs) }}</text>
            </text>
            <text class="code-tip text-danger" v-else>签到码已过期，请重新获取</text>
            <text class="code-tip text-sm text-muted" v-if="expireAt">服务端过期时间：{{ formatDateTime(expireAt) }}</text>
          </view>
          <view class="code-box code-empty" v-else>
            <text class="state-text">点击下方按钮生成一次性签到码（60 秒有效）</text>
          </view>

          <button class="btn btn-primary code-btn" :disabled="codeLoading" @click="fetchCode">
            {{ codeLoading ? '生成中…' : code && remainMs > 0 ? '重新生成签到码' : '获取签到码' }}
          </button>
        </StateView>
      </view>

      <!-- 扫码 / 输入码 -->
      <view class="card">
        <text class="card-title">扫码 / 输入码签到</text>
        <text class="text-sm text-muted scan-tip">
          场馆核验台可输入你的签到码完成核销；也可由核验人员扫码。
        </text>
        <view class="row scan-row">
          <input
            class="code-input"
            v-model="inputCode"
            placeholder="请输入 6 位签到码"
            placeholder-class="ph"
            type="text"
          />
          <button class="btn btn-default btn-sm scan-btn" @click="onScan">扫码</button>
          <button class="btn btn-primary btn-sm scan-btn" :disabled="scanLoading" @click="submitCode">
            提交
          </button>
        </view>
        <view class="scan-result" v-if="scanResult">
          <text class="text-success text-sm">
            核销成功：预约单 #{{ scanResult.reservationId }}，状态 {{ scanResult.status }}，
            {{ formatDateTime(scanResult.checkinTime) }}
          </text>
        </view>
      </view>

      <!-- 签到记录 -->
      <view class="card">
        <view class="row-between">
          <text class="card-title">签到记录</text>
          <text class="text-sm text-primary" @click="loadRecords">刷新</text>
        </view>
        <StateView
          :status="recordState"
          loading-text="正在加载签到记录…"
          :error-text="recordError"
          empty-text="暂无签到记录"
          @retry="loadRecords"
        >
          <view class="rec-item" v-for="r in records" :key="r.id">
            <view class="col flex-1">
              <text class="rec-name">{{ r.userName || '本人' }}</text>
              <text class="text-sm text-muted">{{ formatDateTime(r.checkinTime) }}</text>
            </view>
            <text class="tag tag-blue">{{ r.channel || 'CODE' }}</text>
          </view>
        </StateView>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import StateView from '@/components/StateView.vue'
import { getMyReservations } from '@/api/reservation.js'
import { getCheckinCode, scanCheckin, getCheckinRecords } from '@/api/checkin.js'
import { RESV_STATUS } from '@/config/index.js'
import { countdownText, formatDateTime, shortTime, toTimestamp, toast } from '@/utils/date.js'
import { requireLogin, paramOf } from '@/utils/nav.js'

const pageState = ref('loading')
const errorMsg = ref('加载失败，请稍后重试')
const candidates = ref([])
const current = ref(null)

const code = ref('')
const expireAt = ref('')
const remainMs = ref(0)
const codeLoading = ref(false)

const inputCode = ref('')
const scanLoading = ref(false)
const scanResult = ref(null)

const records = ref([])
const recordState = ref('loading')
const recordError = ref('加载失败，请稍后重试')
/** 从「我的预约」跳转时带过来的预约 id */
const preselectId = ref('')

let timer = null

function statusText(s) {
  return (RESV_STATUS[s] && RESV_STATUS[s].text) || '未知'
}

function startCountdown() {
  stopCountdown()
  const end = toTimestamp(expireAt.value)
  const tick = () => {
    remainMs.value = end - Date.now()
    if (remainMs.value <= 0) {
      remainMs.value = 0
      stopCountdown()
    }
  }
  tick()
  timer = setInterval(tick, 1000)
}
function stopCountdown() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

/** 加载可签到预约（status=1 已确认） */
async function initReservation() {
  pageState.value = 'loading'
  try {
    const data = await getMyReservations({ status: 1, pageNum: 1, pageSize: 20 })
    candidates.value = (data && data.records) || []
    if (!candidates.value.length) {
      // 退回：查询全部，挑出可签到/已签到的预约
      const all = await getMyReservations({ pageNum: 1, pageSize: 20 })
      const list = (all && all.records) || []
      candidates.value = list.filter((r) => r.canCheckin || r.status === 1 || r.status === 2)
    }
    if (candidates.value.length) {
      const pre =
        preselectId.value && candidates.value.find((r) => String(r.id) === String(preselectId.value))
      current.value = pre || candidates.value[0]
      pageState.value = 'success'
      loadRecords()
    } else {
      pageState.value = 'empty'
    }
  } catch (e) {
    errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
    pageState.value = 'error'
  }
}

function switchReservation(r) {
  stopCountdown()
  current.value = r
  code.value = ''
  expireAt.value = ''
  remainMs.value = 0
  scanResult.value = null
  loadRecords()
}

/** POST /checkin/code */
async function fetchCode() {
  if (!current.value) {
    toast('请先选择预约单')
    return
  }
  codeLoading.value = true
  try {
    const data = await getCheckinCode(current.value.id)
    code.value = (data && data.code) || ''
    expireAt.value = (data && data.expireAt) || ''
    startCountdown()
  } catch (e) {
    if (e && e.message) toast(e.message)
  } finally {
    codeLoading.value = false
  }
}

/** 扫码（小程序端调用摄像头；H5 提示手动输入） */
function onScan() {
  if (typeof uni.scanCode !== 'function') {
    toast('当前环境不支持扫码，请手动输入签到码')
    return
  }
  uni.scanCode({
    onlyFromCamera: false,
    scanType: ['qrCode', 'barCode'],
    success: (res) => {
      const value = (res && res.result) || ''
      if (!value) {
        toast('未识别到有效签到码')
        return
      }
      inputCode.value = String(value).trim()
      submitCode()
    },
    fail: () => {
      toast('扫码已取消或不可用，可手动输入签到码')
    }
  })
}

/** POST /checkin/scan */
async function submitCode() {
  const c = inputCode.value.trim()
  if (!c) {
    toast('请输入签到码')
    return
  }
  scanLoading.value = true
  try {
    const data = await scanCheckin(c)
    scanResult.value = data || {}
    inputCode.value = ''
    toast('核销成功', 'success')
    loadRecords()
  } catch (e) {
    // 30007 签到码无效或已过期等，直接展示后端 message
    if (e && e.message) toast(e.message)
  } finally {
    scanLoading.value = false
  }
}

/** GET /checkin/records?reservationId= */
async function loadRecords() {
  recordState.value = 'loading'
  try {
    const data = await getCheckinRecords(current.value ? current.value.id : '')
    records.value = Array.isArray(data) ? data : []
    recordState.value = records.value.length ? 'success' : 'empty'
  } catch (e) {
    recordError.value = (e && e.message) || '加载失败，请稍后重试'
    recordState.value = 'error'
  }
}

function goReservationList() {
  uni.switchTab({
    url: '/pages/reservation/list',
    fail: () => uni.redirectTo({ url: '/pages/reservation/list' })
  })
}

onLoad((options) => {
  if (!requireLogin()) return
  preselectId.value = paramOf(options, 'reservationId')
  initReservation()
})

onUnmounted(() => stopCountdown())
</script>

<style scoped>
.card-title {
  display: block;
  margin-bottom: 12rpx;
}
.resv-pick {
  margin-bottom: 16rpx;
}
.pick-scroll {
  white-space: nowrap;
  margin-top: 12rpx;
}
.pick-row {
  display: flex;
  flex-direction: row;
}
.pick-chip {
  flex-shrink: 0;
  border: 1rpx solid #eceef1;
  border-radius: 10rpx;
  padding: 12rpx 20rpx;
  margin-right: 16rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.pick-chip.active {
  border-color: #1677ff;
  background-color: #f7faff;
}
.pick-no {
  font-size: 26rpx;
  color: #1f2329;
}
.pick-time {
  font-size: 22rpx;
  color: #8a9099;
  margin-top: 4rpx;
}
.code-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 0 24rpx;
}
.code-value {
  font-size: 76rpx;
  font-weight: 700;
  letter-spacing: 12rpx;
  color: #1677ff;
}
.code-value.expired {
  color: #b3b8bf;
  text-decoration: line-through;
}
.code-tip {
  font-size: 26rpx;
  color: #4e5969;
  margin-top: 12rpx;
}
.code-empty {
  padding: 30rpx 0;
}
.code-btn {
  margin-top: 12rpx;
}
.scan-tip {
  display: block;
  margin-bottom: 16rpx;
}
.scan-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.code-input {
  flex: 1;
  height: 80rpx;
  background-color: #f7f8fa;
  border-radius: 10rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
}
.scan-btn {
  margin-left: 16rpx;
  width: 130rpx;
}
.scan-result {
  margin-top: 16rpx;
  padding: 16rpx;
  background-color: #f2fbf0;
  border-radius: 10rpx;
}
.rec-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #f5f6f8;
}
.rec-item:last-child {
  border-bottom: none;
}
.rec-name {
  font-size: 28rpx;
  color: #1f2329;
}
.ph {
  color: #b3b8bf;
}
</style>
