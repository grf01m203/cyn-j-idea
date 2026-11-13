<template>
  <view class="page">
    <view class="page-body">
      <!-- 已确认预约：生成签到码 -->
      <view class="card">
        <view class="row-between">
          <text class="card-title">我的签到码</text>
          <text class="text-sm text-muted" v-if="current">{{ statusText(current.status) }}</text>
        </view>
        <text class="window-tip text-sm text-muted" v-if="windowText">{{ windowText }}</text>

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
            <view class="qr-wrap" v-if="code && remainMs > 0 && qrDataUrl">
              <image class="qr-img" :src="qrDataUrl" mode="aspectFit" />
              <text class="code-tip text-sm text-muted">到馆后向工作人员出示此二维码，由核销台扫码完成核销</text>
            </view>
          </view>
          <view class="code-box code-empty" v-else>
            <text class="state-text">点击下方按钮生成一次性签到码（60 秒有效）</text>
          </view>

          <button class="btn btn-primary code-btn" :disabled="codeLoading" @click="fetchCode">
            {{ codeLoading ? '生成中…' : code && remainMs > 0 ? '重新生成签到码' : '获取签到码' }}
          </button>
        </StateView>
      </view>

      <!-- 核销方式说明 -->
      <view class="card">
        <text class="card-title">如何核销</text>
        <view class="tip-list">
          <text class="tip-item">1. 到达场馆后，向工作人员出示上方二维码或 6 位数字签到码。</text>
          <text class="tip-item">2. 由场馆管理员在管理端「签到记录 → 现场核销台」扫码或输入 6 位码完成核销。</text>
          <text class="tip-item">3. 核销成功后你会收到站内消息「签到成功」，可在「消息」中查看。</text>
          <text class="tip-item">4. 签到码为一次性使用、60 秒有效；过期请点击「重新生成签到码」。</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import StateView from '@/components/StateView.vue'
import { getMyReservations } from '@/api/reservation.js'
import { getCheckinCode, getCheckinWindow } from '@/api/checkin.js'
import { RESV_STATUS } from '@/config/index.js'
import { countdownText, formatDateTime, shortTime, toTimestamp, toast } from '@/utils/date.js'
import { requireLogin, paramOf } from '@/utils/nav.js'
import QRCode from '@/utils/qrcode.js'

const pageState = ref('loading')
const errorMsg = ref('加载失败，请稍后重试')
const candidates = ref([])
const current = ref(null)

const code = ref('')
const expireAt = ref('')
const remainMs = ref(0)
const codeLoading = ref(false)
/** 当前签到码对应的二维码（SVG data URI，由 <image> 展示） */
const qrDataUrl = ref('')

/** 从「我的预约」跳转时带过来的预约 id */
const preselectId = ref('')
/** 管理员配置的签到窗口（开始前后各 N 分钟），用于提示 */
const windowInfo = ref(null)
const windowText = computed(() => {
  const w = windowInfo.value && Number(windowInfo.value.windowMinutes)
  return w ? `签到窗口：开始前 ${w} 分钟 ~ 开始后 ${w} 分钟（由管理员配置）` : ''
})

let timer = null

/** 把签到码编码为二维码 SVG data URI（纯前端，离线可用） */
function buildQrDataUrl(text) {
  if (!text) return ''
  try {
    const qr = new QRCode(6, 1)
    qr.addData(String(text))
    qr.make()
    const n = qr.getModuleCount()
    const quiet = 2
    let rects = ''
    for (let r = 0; r < n; r++) {
      let col = 0
      while (col < n) {
        if (!qr.isDark(r, col)) {
          col++
          continue
        }
        const start = col
        while (col < n && qr.isDark(r, col)) col++
        rects += '<rect x="' + (start + quiet) + '" y="' + (r + quiet) + '" width="' + (col - start) + '" height="1"/>'
      }
    }
    const dim = n + quiet * 2
    const svg =
      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' + dim + ' ' + dim + '" width="440" height="440" shape-rendering="crispEdges">' +
      '<rect width="' + dim + '" height="' + dim + '" fill="#ffffff"/>' +
      '<g fill="#111111">' + rects + '</g></svg>'
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg)
  } catch (e) {
    return ''
  }
}

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
  qrDataUrl.value = ''
  remainMs.value = 0
}

/** POST /checkin/code（学生生成自己的签到码；核销由管理端完成） */
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
    qrDataUrl.value = buildQrDataUrl(code.value)
    startCountdown()
  } catch (e) {
    if (e && e.message) toast(e.message)
  } finally {
    codeLoading.value = false
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
  loadWindow()
  initReservation()
})

async function loadWindow() {
  try {
    windowInfo.value = await getCheckinWindow()
  } catch (e) {
    windowInfo.value = null
  }
}

onUnmounted(() => stopCountdown())
</script>

<style scoped>
.card-title {
  display: block;
  margin-bottom: 12rpx;
}
.window-tip {
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
  display: block;
  max-width: 320rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
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
.qr-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 20rpx;
}
.qr-img {
  width: 340rpx;
  height: 340rpx;
  background-color: #ffffff;
}
.code-empty {
  padding: 30rpx 0;
}
.code-btn {
  margin-top: 12rpx;
}
.tip-list {
  display: flex;
  flex-direction: column;
  margin-top: 8rpx;
}
.tip-item {
  font-size: 26rpx;
  color: #4e5969;
  line-height: 1.7;
}
</style>
