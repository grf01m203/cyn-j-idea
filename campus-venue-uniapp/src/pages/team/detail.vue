<template>
  <view class="page">
    <view class="page-body">
      <StateView
        :status="state"
        loading-text="正在加载拼场详情…"
        :error-text="errorMsg"
        empty-text="拼场不存在或已删除"
        @retry="load"
      >
        <view class="card hero">
          <view class="row-between">
            <text class="hero-title">{{ detail.courtName || '拼场详情' }}</text>
            <text class="tag" :class="statusClass(detail.status)">{{ statusText(detail.status) }}</text>
          </view>
          <text class="hero-sub">
            {{ detail.resvDate }} {{ shortTime(detail.startTime) }} - {{ shortTime(detail.endTime) }}
          </text>
          <view class="progress-wrap">
            <view class="progress-bar">
              <view class="progress-inner" :style="{ width: percent + '%' }"></view>
            </view>
            <text class="progress-text text-sm text-muted">
              {{ detail.currentMembers || 0 }}/{{ detail.maxMembers || 0 }} 人
            </text>
          </view>
          <text class="text-sm text-muted" v-if="detail.remark">备注：{{ detail.remark }}</text>
        </view>

        <view class="card">
          <text class="card-title">基本信息</text>
          <view class="kv"><text class="kv-k">拼场 ID</text><text class="kv-v">{{ detail.id }}</text></view>
          <view class="kv"><text class="kv-k">发起人</text><text class="kv-v">{{ detail.creatorName || '-' }}</text></view>
          <view class="kv"><text class="kv-k">场地</text><text class="kv-v">{{ detail.courtName || '-' }}</text></view>
          <view class="kv"><text class="kv-k">日期</text><text class="kv-v">{{ detail.resvDate || '-' }}</text></view>
          <view class="kv">
            <text class="kv-k">时段</text>
            <text class="kv-v">{{ shortTime(detail.startTime) }} ~ {{ shortTime(detail.endTime) }}</text>
          </view>
          <view class="kv">
            <text class="kv-k">状态</text>
            <text class="kv-v">{{ statusText(detail.status) }}</text>
          </view>
          <view class="kv" v-if="detail.reservationId">
            <text class="kv-k">关联预约单</text>
            <text class="kv-v text-primary" @click="goReservation">#{{ detail.reservationId }} ›</text>
          </view>
        </view>

        <view class="card">
          <view class="row-between">
            <text class="card-title">成员（{{ members.length }}）</text>
            <text class="text-sm text-muted" v-if="isLeader">队长可审核申请</text>
          </view>

          <view v-if="!members.length" class="empty-inline">
            <text class="text-sm text-muted">暂无成员</text>
          </view>

          <view class="member-item" v-for="m in members" :key="m.id">
            <view class="avatar">{{ (m.nickname || 'U').slice(0, 1) }}</view>
            <view class="col flex-1">
              <view class="row">
                <text class="member-name">{{ m.nickname || ('用户' + m.userId) }}</text>
                <text class="tag tag-blue leader-tag" v-if="m.isLeader">队长</text>
                <text class="tag tag-orange leader-tag" v-if="m.isMe">我</text>
              </view>
              <text class="text-sm" :class="memberStatusClass(m.status)">{{ memberStatusText(m.status) }}</text>
            </view>
            <view class="row" v-if="isLeader && Number(m.status) === 0 && !(m.isLeader === true || Number(m.isLeader) === 1)">
              <button class="btn btn-primary btn-sm review-btn" :disabled="reviewing" @click="review(m, true)">
                通过
              </button>
              <button class="btn btn-danger btn-sm review-btn" :disabled="reviewing" @click="review(m, false)">
                拒绝
              </button>
            </view>
          </view>
        </view>
      </StateView>
    </view>

    <!-- 底部操作 -->
    <view class="bottom-bar">
      <button class="btn btn-default bar-btn" @click="goList">拼场广场</button>

      <!-- 申请加入：非成员、招募中 -->
      <button
        v-if="!myMember && (detail.status === 0 || detail.status === 1)"
        class="btn btn-primary bar-btn"
        :disabled="acting"
        @click="onJoin"
      >申请加入</button>

      <!-- 退出：已加入且非队长 -->
      <button
        v-if="myMember && !isLeader && Number(myMember.status) !== 3"
        class="btn btn-danger bar-btn"
        :disabled="acting"
        @click="onQuit"
      >退出拼场</button>

      <!-- 队长：成团 / 解散 -->
      <template v-if="isLeader">
        <button
          v-if="detail.status !== 2 && detail.status !== 3"
          class="btn btn-primary bar-btn"
          :disabled="acting"
          @click="openConfirm"
        >确认成团</button>
        <button
          v-if="detail.status !== 3"
          class="btn btn-danger bar-btn"
          :disabled="acting"
          @click="onDissolve"
        >解散</button>
      </template>
    </view>

    <!-- 成团确认弹窗 -->
    <Modal
      v-model="confirmVisible"
      title="确认成团"
      confirm-text="确认成团"
      :confirm-disabled="acting"
      @confirm="onConfirm"
    >
      <view class="kv"><text class="kv-k">场地</text><text class="kv-v">{{ detail.courtName }}</text></view>
      <view class="kv">
        <text class="kv-k">时段</text>
        <text class="kv-v">{{ detail.resvDate }} {{ shortTime(detail.startTime) }}~{{ shortTime(detail.endTime) }}</text>
      </view>
      <view class="kv">
        <text class="kv-k">当前人数</text>
        <text class="kv-v">{{ detail.currentMembers || 0 }}/{{ detail.maxMembers || 0 }}</text>
      </view>
      <text class="text-sm text-muted">
        成团后将自动生成预约单并推送给全体成员，费用按约定分摊，请在「我的预约」中完成支付。
      </text>
    </Modal>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import StateView from '@/components/StateView.vue'
import Modal from '@/components/Modal.vue'
import { getTeamDetail, joinTeam, reviewTeamMember, quitTeam, confirmTeam, dissolveTeam } from '@/api/team.js'
import { TEAM_STATUS, MEMBER_STATUS } from '@/config/index.js'
import { shortTime, toast } from '@/utils/date.js'
import { requireLogin, paramOf } from '@/utils/nav.js'

const id = ref('')
const detail = ref({})
const state = ref('loading')
const errorMsg = ref('加载失败，请稍后重试')
const acting = ref(false)
const reviewing = ref(false)
const confirmVisible = ref(false)
let loaded = false

const members = computed(() => (detail.value && detail.value.members) || [])
/**
 * 是否队长：契约未在拼场详情顶层定义 isLeader，
 * 因此优先看成员里的 isMe + isLeader，其次退回 isCreator。
 */
const isLeader = computed(() => {
  if (detail.value && detail.value.isCreator === true) return true
  return members.value.some(
    (m) => (m.isMe === true || Number(m.isMe) === 1) && (m.isLeader === true || Number(m.isLeader) === 1)
  )
})
const myMember = computed(
  () => members.value.find((m) => m.isMe === true || Number(m.isMe) === 1) || null
)
const percent = computed(() => {
  const max = Number(detail.value.maxMembers || 0)
  if (!max) return 0
  return Math.min(100, Math.round((Number(detail.value.currentMembers || 0) / max) * 100))
})

function statusText(s) {
  return (TEAM_STATUS[s] && TEAM_STATUS[s].text) || '未知'
}
function statusClass(s) {
  if (s === 2) return 'tag-green'
  if (s === 1) return 'tag-orange'
  if (s === 3) return 'tag-gray'
  return 'tag-blue'
}
function memberStatusText(s) {
  return (MEMBER_STATUS[s] && MEMBER_STATUS[s].text) || '未知'
}
function memberStatusClass(s) {
  if (s === 1) return 'text-success'
  if (s === 2) return 'text-danger'
  return 'text-muted'
}

async function load() {
  state.value = 'loading'
  try {
    const data = await getTeamDetail(id.value)
    detail.value = data || {}
    state.value = data ? 'success' : 'empty'
  } catch (e) {
    errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
    state.value = 'error'
  }
}

/** 申请加入 */
async function onJoin() {
  if (acting.value) return
  acting.value = true
  try {
    const data = await joinTeam(id.value)
    uni.showModal({
      title: '申请已提交',
      content: `等待队长审核（申请状态：${memberStatusText(data && data.status)}）\n队长会收到消息通知`,
      showCancel: false
    })
    load()
  } catch (e) {
    if (e && e.message) toast(e.message)
  } finally {
    acting.value = false
  }
}

/** 队长审核 */
async function review(member, accept) {
  if (reviewing.value) return
  reviewing.value = true
  try {
    await reviewTeamMember(id.value, member.id, accept)
    toast(accept ? '已通过申请' : '已拒绝申请', 'success')
    load()
  } catch (e) {
    if (e && e.message) toast(e.message)
  } finally {
    reviewing.value = false
  }
}

/** 退出拼场 */
function onQuit() {
  uni.showModal({
    title: '退出拼场',
    content: '确认退出该拼场吗？',
    success: async (res) => {
      if (!res.confirm) return
      acting.value = true
      try {
        await quitTeam(id.value)
        toast('已退出拼场', 'success')
        load()
      } catch (e) {
        if (e && e.message) toast(e.message)
      } finally {
        acting.value = false
      }
    }
  })
}

function openConfirm() {
  confirmVisible.value = true
}

/** 确认成团 -> {reservationId} */
async function onConfirm() {
  if (acting.value) return
  acting.value = true
  try {
    const data = await confirmTeam(id.value)
    confirmVisible.value = false
    uni.showModal({
      title: '成团成功',
      content: `已生成预约单 #${(data && data.reservationId) || '-'}，已向全体成员推送消息`,
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
  } finally {
    acting.value = false
  }
}

/** 解散拼场 */
function onDissolve() {
  uni.showModal({
    title: '解散拼场',
    content: '解散后不可恢复，已加入的成员会收到通知。确认解散？',
    success: async (res) => {
      if (!res.confirm) return
      acting.value = true
      try {
        await dissolveTeam(id.value)
        toast('拼场已解散', 'success')
        load()
      } catch (e) {
        if (e && e.message) toast(e.message)
      } finally {
        acting.value = false
      }
    }
  })
}

function goList() {
  uni.switchTab({
    url: '/pages/team/list',
    fail: () => uni.redirectTo({ url: '/pages/team/list' })
  })
}

function goReservation() {
  uni.navigateTo({ url: `/pages/reservation/detail?id=${detail.value.reservationId}` })
}

onLoad((options) => {
  if (!requireLogin()) return
  id.value = paramOf(options, 'id')
  load().then(() => {
    loaded = true
  })
})

onShow(() => {
  if (loaded) load()
})
</script>

<style scoped>
.hero {
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #1677ff 0%, #4791ff 100%);
}
.hero-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #ffffff;
  max-width: 420rpx;
}
.hero .tag {
  background-color: rgba(255, 255, 255, 0.22);
  color: #ffffff;
}
.hero-sub {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.9);
  margin-top: 12rpx;
}
.progress-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin: 18rpx 0 12rpx;
}
.progress-bar {
  flex: 1;
  height: 14rpx;
  background-color: rgba(255, 255, 255, 0.35);
  border-radius: 7rpx;
  overflow: hidden;
  margin-right: 16rpx;
}
.progress-inner {
  height: 14rpx;
  background-color: #ffffff;
  border-radius: 7rpx;
}
.progress-text {
  color: rgba(255, 255, 255, 0.9);
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
}
.empty-inline {
  padding: 20rpx 0;
}
.member-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 18rpx 0;
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
.leader-tag {
  margin-left: 12rpx;
}
.review-btn {
  margin-left: 12rpx;
  width: 120rpx;
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
  flex-wrap: wrap;
  z-index: 80;
}
.bar-btn {
  flex: 1;
  min-width: 200rpx;
  margin-left: 12rpx;
  margin-top: 8rpx;
}
.bar-btn:first-child {
  margin-left: 0;
}
</style>
