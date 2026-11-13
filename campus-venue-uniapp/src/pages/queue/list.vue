<template>
  <view class="page">
    <view class="page-body">
      <view class="card notice-card">
        <text class="text-sm text-muted">
          候补机制：有用户取消预约时，系统按排队顺序推送「递补通知」，需在 10 分钟内确认，超时自动顺延给下一位。
        </text>
        <view class="row-between ticker-row">
          <text class="text-sm text-muted">当前时间</text>
          <text class="text-sm">{{ nowText }}</text>
        </view>
      </view>

      <StateView
        :status="state"
        loading-text="正在加载候补记录…"
        :error-text="errorMsg"
        empty-text="暂无候补记录"
        empty-action-text="去场馆逛逛"
        :show-empty-action="true"
        @retry="load"
        @action="goVenue"
      >
        <view class="queue-card" v-for="q in list" :key="q.id">
          <view class="row-between">
            <text class="queue-title ellipsis">{{ q.courtName }}</text>
            <text class="tag" :class="statusClass(q.status)">{{ statusText(q.status) }}</text>
          </view>

          <view class="resv-line">
            <text class="text-sm text-muted">📅 {{ q.resvDate }}</text>
            <text class="text-sm text-muted">🕘 {{ shortTime(q.startTime) }} - {{ shortTime(q.endTime) }}</text>
          </view>

          <view class="resv-line">
            <text class="text-sm text-muted">排队位置：第 {{ q.position || '-' }} 位</text>
            <text class="text-sm text-muted" v-if="q.expireTime">截止：{{ shortTime(q.expireTime) }}</text>
          </view>

          <!-- 递补通知中：倒计时 + 确认 -->
          <view class="notify-box" v-if="Number(q.status) === 1">
            <view class="row-between">
              <text class="text-sm text-bold" :class="remainOf(q) > 0 ? 'text-primary' : 'text-danger'">
                {{ remainOf(q) > 0 ? '请尽快确认，剩余 ' + countdownText(remainOf(q)) : '已超时，等待系统顺延' }}
              </text>
              <text class="text-sm text-muted">{{ formatDateTime(q.expireTime) }}</text>
            </view>
          </view>

          <!-- 已生成待支付预约：引导去支付 -->
          <view class="notify-box pay-box" v-if="canPayOf(q)">
            <text class="text-sm text-bold text-primary">已生成待支付预约，请完成支付后占位</text>
            <text class="text-sm text-muted" v-if="resvIdOf(q)">关联预约 #{{ resvIdOf(q) }}</text>
          </view>

          <view class="row ops" v-if="showOps(q)">
            <button
              v-if="canPayOf(q)"
              class="btn btn-primary btn-sm op"
              :disabled="actingId === q.id"
              @click="goPay(q)"
            >去支付</button>
            <button
              v-if="Number(q.status) === 1"
              class="btn btn-primary btn-sm op"
              :disabled="actingId === q.id || remainOf(q) <= 0"
              @click="onConfirm(q)"
            >确认递补</button>
            <button
              v-if="Number(q.status) === 0 || Number(q.status) === 1"
              class="btn btn-danger btn-sm op"
              :disabled="actingId === q.id"
              @click="onCancel(q)"
            >取消候补</button>
          </view>
        </view>
      </StateView>
    </view>
  </view>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import StateView from '@/components/StateView.vue'
import { getMyQueues, confirmQueue, cancelQueue } from '@/api/queue.js'
import { QUEUE_STATUS } from '@/config/index.js'
import { countdownText, formatDateTime, shortTime, toTimestamp, toast } from '@/utils/date.js'
import { requireLogin, isLogin } from '@/utils/nav.js'

const list = ref([])
const state = ref('loading')
const errorMsg = ref('加载失败，请稍后重试')
const actingId = ref('')
const now = ref(Date.now())
let timer = null

const nowText = ref('')

function tick() {
  now.value = Date.now()
  const d = new Date(now.value)
  const p = (n) => (n < 10 ? '0' + n : '' + n)
  nowText.value = `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function startTicker() {
  tick()
  if (!timer) timer = setInterval(tick, 1000)
}
function stopTicker() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function statusText(s) {
  return (QUEUE_STATUS[s] && QUEUE_STATUS[s].text) || '未知'
}
function statusClass(s) {
  if (s === 1) return 'tag-orange'
  if (s === 2) return 'tag-green'
  if (s === 0) return 'tag-blue'
  return 'tag-gray'
}
/** 距离截止剩余毫秒（依赖 now 触发重算） */
function remainOf(q) {
  if (!q || !q.expireTime) return 0
  return toTimestamp(q.expireTime) - now.value
}

/** 关联预约 id（兼容 reservationId / reservation_id） */
function resvIdOf(q) {
  if (!q) return ''
  const v = q.reservationId != null ? q.reservationId : q.reservation_id
  return v === null || v === undefined || v === '' || Number(v) === 0 ? '' : v
}

/** 是否可去支付：已通知/已确认，且 canPay=true 或已生成预约 */
function canPayOf(q) {
  if (!q) return false
  const s = Number(q.status)
  if (s !== 1 && s !== 2) return false
  const canPay = q.canPay === true || Number(q.canPay) === 1
  return canPay || !!resvIdOf(q)
}

/** 操作区是否展示：等待中 / 已通知 / 可去支付 */
function showOps(q) {
  if (!q) return false
  const s = Number(q.status)
  return s === 0 || s === 1 || canPayOf(q)
}

/** 去支付：跳转预约详情页完成支付 */
function goPay(q) {
  const rid = resvIdOf(q)
  if (!rid) {
    toast('预约信息尚未生成，请稍后刷新')
    load()
    return
  }
  uni.navigateTo({ url: `/pages/reservation/detail?id=${rid}` })
}

async function load() {
  state.value = 'loading'
  try {
    const data = await getMyQueues()
    list.value = Array.isArray(data) ? data : []
    state.value = list.value.length ? 'success' : 'empty'
  } catch (e) {
    errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
    state.value = 'error'
  }
}

async function onConfirm(q) {
  if (actingId.value) return
  actingId.value = q.id
  try {
    const data = await confirmQueue(q.id)
    const needPay = data && (data.needPay === true || Number(data.needPay) === 1)
    // 递补确认后生成「待支付」预约，直接引导去支付
    if (needPay) {
      toast('已生成待支付预约，请完成支付')
      const rid = (data && data.reservationId) || resvIdOf(q)
      if (rid) {
        uni.navigateTo({ url: `/pages/reservation/detail?id=${rid}` })
      } else {
        load()
      }
      return
    }
    uni.showModal({
      title: '递补成功',
      content: `已生成预约单 #${(data && data.reservationId) || '-'}，请在「我的预约」中完成支付`,
      confirmText: '查看预约',
      cancelText: '留在本页',
      success: (res) => {
        if (res.confirm && data && data.reservationId) {
          uni.navigateTo({ url: `/pages/reservation/detail?id=${data.reservationId}` })
        } else {
          load()
        }
      }
    })
  } catch (e) {
    if (e && e.message) toast(e.message)
    load()
  } finally {
    actingId.value = ''
  }
}

function onCancel(q) {
  uni.showModal({
    title: '取消候补',
    content: '取消后需重新排队，确认取消？',
    success: async (res) => {
      if (!res.confirm) return
      actingId.value = q.id
      try {
        await cancelQueue(q.id)
        toast('已取消候补', 'success')
        load()
      } catch (e) {
        if (e && e.message) toast(e.message)
      } finally {
        actingId.value = ''
      }
    }
  })
}

function goVenue() {
  uni.switchTab({
    url: '/pages/venue/list',
    fail: () => uni.navigateTo({ url: '/pages/venue/list' })
  })
}

onShow(() => {
  if (!requireLogin()) return
  startTicker()
  load()
})

onPullDownRefresh(async () => {
  if (!isLogin()) {
    uni.stopPullDownRefresh()
    return
  }
  await load()
  uni.stopPullDownRefresh()
})

onUnmounted(() => stopTicker())
</script>

<style scoped>
.notice-card {
  background-color: #f7faff;
}
.ticker-row {
  margin-top: 12rpx;
  padding-top: 12rpx;
  border-top: 1rpx solid #e8f1ff;
}
.queue-card {
  background-color: #ffffff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}
.queue-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1f2329;
  max-width: 420rpx;
}
.resv-line {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin-top: 14rpx;
}
.notify-box {
  margin-top: 18rpx;
  padding: 16rpx;
  background-color: #fff7e6;
  border-radius: 10rpx;
}
.pay-box {
  display: flex;
  flex-direction: column;
  background-color: #f0f7ff;
}
.pay-box text {
  margin-top: 6rpx;
}
.pay-box text:first-child {
  margin-top: 0;
}
.ops {
  margin-top: 18rpx;
  justify-content: flex-end;
}
.op {
  margin-left: 12rpx;
}
</style>
