<template>
  <view class="page">
    <!-- 状态筛选 -->
    <scroll-view class="tabs" scroll-x :show-scrollbar="false">
      <view class="tabs-row">
        <view
          v-for="t in tabs"
          :key="String(t.value)"
          class="tab"
          :class="{ active: String(status) === String(t.value) }"
          @click="switchTab(t.value)"
        >
          <text>{{ t.label }}</text>
        </view>
      </view>
    </scroll-view>

    <view class="page-body">
      <StateView
        :status="state"
        loading-text="正在加载预约…"
        :error-text="errorMsg"
        empty-text="暂无相关预约记录"
        empty-action-text="去预约场馆"
        :show-empty-action="true"
        @retry="load(true)"
        @action="goVenue"
      >
        <view class="resv-card" v-for="item in list" :key="item.id" @click="goDetail(item)">
          <view class="row-between">
            <text class="resv-no ellipsis">{{ item.resvNo || ('#' + item.id) }}</text>
            <text class="tag" :class="statusTagClass(item.status)">
              {{ statusText(item.status) }}
            </text>
          </view>

          <text class="resv-title">{{ item.venueName }} · {{ item.courtName }}</text>

          <view class="resv-line">
            <text class="text-sm text-muted">📅 {{ item.resvDate }}</text>
            <text class="text-sm text-muted">🕘 {{ shortTime(item.startTime) }} - {{ shortTime(item.endTime) }}</text>
          </view>

          <view class="resv-cd" v-if="countdownText(item)">
            <text class="resv-cd-text">{{ countdownText(item) }}</text>
          </view>

          <view class="row-between resv-foot">
            <view class="row">
              <text class="amount">¥{{ money(item.amount) }}</text>
              <text class="tag tag-gray source-tag" v-if="item.source">{{ sourceText(item.source) }}</text>
              <text class="tag tag-blue source-tag" v-if="item.teamId">拼场</text>
            </view>
            <view class="row ops" @click.stop>
              <button
                v-if="item.canPay"
                class="btn btn-primary btn-sm op"
                @click="onPay(item)"
              >模拟支付</button>
              <button
                v-if="item.canCheckin"
                class="btn btn-plain btn-sm op"
                @click="onCheckin(item)"
              >去签到</button>
              <button class="btn btn-default btn-sm op" @click="goDetail(item)">详情</button>
              <button
                v-if="item.canCancel"
                class="btn btn-danger btn-sm op"
                @click="openCancel(item)"
              >取消</button>
            </view>
          </view>
        </view>

        <view class="load-more" v-if="list.length">
          <text v-if="loadingMore">加载中…</text>
          <text v-else-if="finished">— 已加载全部 {{ total }} 条 —</text>
          <text v-else @click="loadMore">点击加载更多</text>
        </view>
      </StateView>
    </view>

    <!-- 取消预约弹窗（带违约提示） -->
    <Modal
      v-model="cancelVisible"
      title="取消预约"
      confirm-text="确认取消"
      :confirm-disabled="cancelling"
      @confirm="submitCancel"
    >
      <view class="kv"><text class="kv-k">单号</text><text class="kv-v">{{ current && current.resvNo }}</text></view>
      <view class="kv">
        <text class="kv-k">时段</text>
        <text class="kv-v">{{ current && current.resvDate }} {{ current && shortTime(current.startTime) }}</text>
      </view>
      <view class="kv-col">
        <text class="kv-k">取消原因</text>
        <input class="reason-input" v-model="reason" placeholder="选填，如：临时有事" placeholder-class="ph" />
      </view>
      <text class="text-sm text-muted">
        临近开场取消可能产生违约记录并扣减信用分，具体以服务端返回的违约金为准。
      </text>
    </Modal>
  </view>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { onShow, onHide, onUnload, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import StateView from '@/components/StateView.vue'
import Modal from '@/components/Modal.vue'
import { getMyReservations, payMock, cancelReservation } from '@/api/reservation.js'
import { RESV_STATUS, PAGE_SIZE } from '@/config/index.js'
import { money, shortTime, toast, toTimestamp, durationText } from '@/utils/date.js'
import { requireLogin, isLogin } from '@/utils/nav.js'
import { useNoticeStore } from '@/store/notice.js'

const noticeStore = useNoticeStore()

const tabs = [
  { label: '全部', value: '' },
  { label: '待支付', value: 0 },
  { label: '已确认', value: 1 },
  { label: '已签到', value: 2 },
  { label: '已完成', value: 3 },
  { label: '未签到', value: 4 },
  { label: '已取消', value: 5 },
  { label: '已过期', value: 6 },
  { label: '逾期取消', value: 7 }
]

const status = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const state = ref('loading')
const errorMsg = ref('加载失败，请稍后重试')
const loadingMore = ref(false)
const finished = ref(false)

const cancelVisible = ref(false)
const cancelling = ref(false)
const reason = ref('')
const current = ref(null)

const nowMs = ref(Date.now())
let ticker = null

function startTicker() {
  stopTicker()
  ticker = setInterval(() => {
    nowMs.value = Date.now()
  }, 1000)
}
function stopTicker() {
  if (ticker) {
    clearInterval(ticker)
    ticker = null
  }
}

/** 距开始 / 距结束倒计时（仅待支付、已确认、已签到且有完整时间时展示） */
function countdownText(item) {
  if (!item) return ''
  const st = Number(item.status)
  if (![0, 1, 2].includes(st)) return ''
  const start = toTimestamp(item.startTime)
  const end = toTimestamp(item.endTime)
  if (!start || !end) return ''
  const now = nowMs.value
  if (now < start) return `距开始 ${durationText(start - now)}`
  if (now < end) return `距结束 ${durationText(end - now)}`
  return ''
}

function statusText(s) {
  const st = Number(s)
  return (RESV_STATUS[st] && RESV_STATUS[st].text) || '未知状态'
}
function statusTagClass(s) {
  const st = Number(s)
  if (st === 1 || st === 2) return 'tag-green'
  if (st === 0) return 'tag-orange'
  if (st === 4 || st === 7) return 'tag-red'
  return 'tag-gray'
}
function sourceText(s) {
  const v = Number(s)
  if (v === 2) return '拼场成团'
  if (v === 3) return '候补递补'
  if (v === 1) return '自主预约'
  return s === 'TEAM' ? '拼场成团' : s === 'QUEUE' ? '候补递补' : String(s || '自主预约')
}

async function load(reset = false) {
  if (reset) {
    pageNum.value = 1
    finished.value = false
    state.value = 'loading'
  }
  try {
    const data = await getMyReservations({
      status: status.value,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE
    })
    const records = (data && data.records) || []
    total.value = (data && data.total) || 0
    list.value = reset ? records : list.value.concat(records)
    finished.value = list.value.length >= total.value || records.length === 0
    state.value = list.value.length ? 'success' : 'empty'
  } catch (e) {
    errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
    if (reset || !list.value.length) state.value = 'error'
  } finally {
    loadingMore.value = false
  }
}

function loadMore() {
  if (loadingMore.value || finished.value) return
  loadingMore.value = true
  pageNum.value += 1
  load(false)
}

function switchTab(v) {
  if (String(status.value) === String(v)) return
  status.value = v
  load(true)
}

function goDetail(item) {
  uni.navigateTo({ url: `/pages/reservation/detail?id=${item.id}` })
}

function goCheckin(item) {
  uni.navigateTo({ url: `/pages/checkin/index?reservationId=${item.id}` })
}

function goVenue() {
  uni.switchTab({
    url: '/pages/venue/list',
    fail: () => uni.navigateTo({ url: '/pages/venue/list' })
  })
}

function onCheckin(item) {
  goCheckin(item)
}

/** 模拟支付 */
async function onPay(item) {
  try {
    const data = await payMock(item.id)
    uni.showToast({ title: `支付成功（状态 ${data && data.status}）`, icon: 'none' })
    setTimeout(() => load(true), 600)
  } catch (e) {
    if (e && e.message) toast(e.message)
  }
}

function openCancel(item) {
  current.value = item
  reason.value = ''
  cancelVisible.value = true
}

async function submitCancel() {
  if (cancelling.value || !current.value) return
  cancelling.value = true
  try {
    const data = await cancelReservation(current.value.id, reason.value)
    cancelVisible.value = false
    const penalty = data && data.penalty
    uni.showModal({
      title: '已取消',
      content: penalty ? `已取消预约，产生违约金 ¥${money(penalty)}` : '预约已取消',
      showCancel: false
    })
    load(true)
  } catch (e) {
    if (e && e.message) toast(e.message)
  } finally {
    cancelling.value = false
  }
}

onShow(() => {
  if (!requireLogin()) return
  noticeStore.refreshUnread()
  startTicker()
  load(true)
})

onHide(stopTicker)
onUnload(stopTicker)
onUnmounted(stopTicker)

onPullDownRefresh(async () => {
  if (!isLogin()) {
    uni.stopPullDownRefresh()
    return
  }
  await load(true)
  uni.stopPullDownRefresh()
})

onReachBottom(() => loadMore())
</script>

<style scoped>
.tabs {
  white-space: nowrap;
  background-color: #ffffff;
  padding: 0 12rpx;
  border-bottom: 1rpx solid #f2f3f5;
}
.tabs-row {
  display: flex;
  flex-direction: row;
}
.tab {
  flex-shrink: 0;
  padding: 24rpx 22rpx;
  font-size: 28rpx;
  color: #4e5969;
  position: relative;
}
.tab.active {
  color: #1677ff;
  font-weight: 600;
}
.tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 6rpx;
  width: 36rpx;
  height: 6rpx;
  margin-left: -18rpx;
  border-radius: 3rpx;
  background-color: #1677ff;
}
.resv-card {
  background-color: #ffffff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}
.resv-no {
  font-size: 26rpx;
  color: #4e5969;
  max-width: 420rpx;
}
.resv-cd {
  margin-top: 12rpx;
}
.resv-cd-text {
  font-size: 26rpx;
  font-weight: 600;
  color: #1677ff;
  font-variant-numeric: tabular-nums;
}
.resv-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #1f2329;
  margin-top: 14rpx;
}
.resv-line {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin-top: 12rpx;
}
.resv-foot {
  margin-top: 20rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid #f5f6f8;
  flex-wrap: wrap;
}
.amount {
  font-size: 32rpx;
  color: #ff4d4f;
  font-weight: 700;
}
.source-tag {
  margin-left: 12rpx;
}
.ops {
  flex-wrap: wrap;
  justify-content: flex-end;
}
.op {
  margin-left: 12rpx;
  margin-top: 8rpx;
}
.kv {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 14rpx 0;
  border-bottom: 1rpx solid #f5f6f8;
}
.kv-k {
  font-size: 26rpx;
  color: #8a9099;
}
.kv-v {
  font-size: 26rpx;
  color: #1f2329;
}
.kv-col {
  padding: 14rpx 0;
}
.kv-col .kv-k {
  display: block;
  margin-bottom: 8rpx;
}
.reason-input {
  height: 72rpx;
  background-color: #f7f8fa;
  border-radius: 10rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
}
.ph {
  color: #b3b8bf;
}
</style>
