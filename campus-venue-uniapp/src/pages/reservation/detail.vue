<template>
  <view class="page">
    <view class="page-body">
      <StateView
        :status="state"
        loading-text="正在加载预约详情…"
        :error-text="errorMsg"
        empty-text="预约不存在"
        @retry="load"
      >
        <view class="card hero">
          <text class="tag" :class="statusTagClass(detail.status)">{{ statusText(detail.status) }}</text>
          <text class="hero-title">{{ detail.venueName }} · {{ detail.courtName }}</text>
          <text class="hero-sub">{{ detail.resvDate }} {{ shortTime(detail.startTime) }} - {{ shortTime(detail.endTime) }}</text>
        </view>

        <view class="card">
          <text class="card-title">预约信息</text>
          <view class="kv"><text class="kv-k">预约单号</text><text class="kv-v">{{ detail.resvNo || '-' }}</text></view>
          <view class="kv"><text class="kv-k">预约 ID</text><text class="kv-v">{{ detail.id }}</text></view>
          <view class="kv"><text class="kv-k">日期</text><text class="kv-v">{{ detail.resvDate || '-' }}</text></view>
          <view class="kv">
            <text class="kv-k">时段</text>
            <text class="kv-v">{{ shortTime(detail.startTime) }} ~ {{ shortTime(detail.endTime) }}</text>
          </view>
          <view class="kv">
            <text class="kv-k">金额</text>
            <text class="kv-v text-danger text-bold">¥{{ money(detail.amount) }}</text>
          </view>
          <view class="kv"><text class="kv-k">来源</text><text class="kv-v">{{ sourceText(detail.source) }}</text></view>
          <view class="kv" v-if="detail.teamId"><text class="kv-k">拼场 ID</text><text class="kv-v">{{ detail.teamId }}</text></view>
          <view class="kv" v-if="detail.remark"><text class="kv-k">备注</text><text class="kv-v">{{ detail.remark }}</text></view>
          <view class="kv" v-if="detail.createTime"><text class="kv-k">创建时间</text><text class="kv-v">{{ formatDateTime(detail.createTime) }}</text></view>
        </view>

        <view class="card">
          <view class="row-between">
            <text class="card-title">参与成员</text>
            <text class="text-sm text-muted">{{ members.length }} 人</text>
          </view>
          <view v-if="!members.length" class="empty-inline">
            <text class="text-sm text-muted">暂无其他成员（单人预约）</text>
          </view>
          <view v-else class="member-list">
            <view class="member-item" v-for="m in members" :key="m.userId">
              <view class="avatar">{{ (m.nickname || 'U').slice(0, 1) }}</view>
              <view class="col flex-1">
                <text class="member-name">{{ m.nickname || ('用户' + m.userId) }}</text>
                <text class="text-sm text-muted">用户 ID：{{ m.userId }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="card" v-if="detail.teamId">
          <view class="row-between" @click="goTeam">
            <text class="card-title">关联拼场</text>
            <text class="text-primary text-sm">查看拼场详情 ›</text>
          </view>
        </view>
      </StateView>
    </view>

    <view class="bottom-bar">
      <button class="btn btn-default bar-btn" @click="goList">返回列表</button>
      <button v-if="detail.canPay" class="btn btn-primary bar-btn" @click="onPay">模拟支付</button>
      <button v-if="detail.canCheckin" class="btn btn-primary bar-btn" @click="goCheckin">去签到</button>
      <button v-if="detail.canCancel" class="btn btn-danger bar-btn" @click="openCancel">取消预约</button>
    </view>

    <Modal
      v-model="cancelVisible"
      title="取消预约"
      confirm-text="确认取消"
      :confirm-disabled="cancelling"
      @confirm="submitCancel"
    >
      <view class="kv-col">
        <text class="kv-k">取消原因</text>
        <input class="reason-input" v-model="reason" placeholder="选填" placeholder-class="ph" />
      </view>
      <text class="text-sm text-muted">取消可能产生违约金并影响信用分，以服务端返回为准。</text>
    </Modal>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import StateView from '@/components/StateView.vue'
import Modal from '@/components/Modal.vue'
import { getReservationDetail, payMock, cancelReservation } from '@/api/reservation.js'
import { RESV_STATUS } from '@/config/index.js'
import { money, shortTime, formatDateTime, toast } from '@/utils/date.js'
import { requireLogin, paramOf } from '@/utils/nav.js'

const id = ref('')
const detail = ref({})
const state = ref('loading')
const errorMsg = ref('加载失败，请稍后重试')
const cancelVisible = ref(false)
const cancelling = ref(false)
const reason = ref('')

const members = computed(() => (detail.value && detail.value.members) || [])

function statusText(s) {
  return (RESV_STATUS[s] && RESV_STATUS[s].text) || '未知'
}
function statusTagClass(s) {
  if (s === 1 || s === 2) return 'tag-green'
  if (s === 0) return 'tag-orange'
  if (s === 4 || s === 7) return 'tag-red'
  return 'tag-gray'
}
function sourceText(s) {
  return s === 'TEAM' ? '拼场成团' : s === 'QUEUE' ? '候补递补' : s || '自主预约'
}

async function load() {
  state.value = 'loading'
  try {
    const data = await getReservationDetail(id.value)
    detail.value = data || {}
    state.value = data ? 'success' : 'empty'
  } catch (e) {
    errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
    state.value = 'error'
  }
}

async function onPay() {
  try {
    await payMock(id.value)
    toast('支付成功', 'success')
    setTimeout(load, 600)
  } catch (e) {
    if (e && e.message) toast(e.message)
  }
}

function openCancel() {
  reason.value = ''
  cancelVisible.value = true
}

async function submitCancel() {
  if (cancelling.value) return
  cancelling.value = true
  try {
    const data = await cancelReservation(id.value, reason.value)
    cancelVisible.value = false
    uni.showModal({
      title: '已取消',
      content: data && data.penalty ? `违约金 ¥${money(data.penalty)}` : '预约已取消',
      showCancel: false
    })
    load()
  } catch (e) {
    if (e && e.message) toast(e.message)
  } finally {
    cancelling.value = false
  }
}

function goList() {
  uni.switchTab({
    url: '/pages/reservation/list',
    fail: () => uni.redirectTo({ url: '/pages/reservation/list' })
  })
}

function goCheckin() {
  uni.navigateTo({ url: `/pages/checkin/index?reservationId=${id.value}` })
}

function goTeam() {
  uni.navigateTo({ url: `/pages/team/detail?id=${detail.value.teamId}` })
}

onLoad((options) => {
  if (!requireLogin()) return
  id.value = paramOf(options, 'id')
  load()
})
</script>

<style scoped>
.hero {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  background: linear-gradient(135deg, #1677ff 0%, #4791ff 100%);
}
.hero-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #ffffff;
  margin-top: 18rpx;
}
.hero-sub {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.9);
  margin-top: 10rpx;
}
.hero .tag {
  background-color: rgba(255, 255, 255, 0.22);
  color: #ffffff;
}
.card-title {
  display: block;
  margin-bottom: 12rpx;
}
.kv {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f5f6f8;
}
.kv:last-child {
  border-bottom: none;
}
.kv-k {
  font-size: 26rpx;
  color: #8a9099;
}
.kv-v {
  font-size: 26rpx;
  color: #1f2329;
  max-width: 420rpx;
  text-align: right;
}
.empty-inline {
  padding: 20rpx 0;
}
.member-list {
  margin-top: 8rpx;
}
.member-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f5f6f8;
}
.member-item:last-child {
  border-bottom: none;
}
.avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background-color: #e8f1ff;
  color: #1677ff;
  font-size: 30rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
}
.member-name {
  font-size: 28rpx;
  color: #1f2329;
}
.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: #ffffff;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: row;
  z-index: 80;
}
.bar-btn {
  flex: 1;
  margin-left: 12rpx;
}
.bar-btn:first-child {
  margin-left: 0;
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
