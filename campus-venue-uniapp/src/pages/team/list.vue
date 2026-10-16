<template>
  <view class="page">
    <view class="page-body">
      <!-- 筛选：日期 -->
      <view class="filter-card">
        <view class="row-between">
          <text class="card-title">拼场广场</text>
          <view class="row">
            <text class="text-sm text-muted filter-tip">{{ date || '全部日期' }}</text>
            <text class="text-sm text-primary clear" @click="clearDate">清除</text>
          </view>
        </view>
        <DatePicker v-model="date" :days="14" @change="load" />
        <view class="row-between court-filter" v-if="courtId">
          <text class="text-sm text-muted">已按场地筛选（courtId={{ courtId }}）</text>
          <text class="text-sm text-primary" @click="clearCourt">取消筛选</text>
        </view>
      </view>

      <StateView
        :status="state"
        loading-text="正在加载拼场…"
        :error-text="errorMsg"
        empty-text="暂无拼场，去场馆详情发起一个吧"
        empty-action-text="去场馆列表"
        :show-empty-action="true"
        @retry="load"
        @action="goVenue"
      >
        <view class="team-card" v-for="t in list" :key="t.id" @click="goDetail(t)">
          <view class="row-between">
            <text class="team-title ellipsis">{{ t.courtName }}</text>
            <text class="tag" :class="statusClass(t.status)">{{ statusText(t.status) }}</text>
          </view>

          <view class="resv-line">
            <text class="text-sm text-muted">📅 {{ t.resvDate }}</text>
            <text class="text-sm text-muted">🕘 {{ shortTime(t.startTime) }} - {{ shortTime(t.endTime) }}</text>
          </view>

          <view class="progress-wrap">
            <view class="progress-bar">
              <view class="progress-inner" :style="{ width: percent(t) + '%' }"></view>
            </view>
            <text class="progress-text text-sm">
              {{ t.currentMembers || 0 }}/{{ t.maxMembers || 0 }} 人
            </text>
          </view>

          <view class="row-between team-foot">
            <text class="text-sm text-muted">发起人：{{ t.creatorName || '-' }}</text>
            <view class="row">
              <text class="tag tag-green" v-if="t.joined">已加入</text>
              <text class="tag tag-blue" v-if="t.isCreator">我是队长</text>
            </view>
          </view>
        </view>
      </StateView>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import StateView from '@/components/StateView.vue'
import DatePicker from '@/components/DatePicker.vue'
import { getTeams } from '@/api/team.js'
import { TEAM_STATUS } from '@/config/index.js'
import { shortTime } from '@/utils/date.js'
import { requireLogin, isLogin } from '@/utils/nav.js'

const date = ref('')
const courtId = ref('')
const list = ref([])
const state = ref('loading')
const errorMsg = ref('加载失败，请稍后重试')

function statusText(s) {
  return (TEAM_STATUS[s] && TEAM_STATUS[s].text) || '未知'
}
function statusClass(s) {
  if (s === 2) return 'tag-green'
  if (s === 1) return 'tag-orange'
  if (s === 3) return 'tag-gray'
  return 'tag-blue'
}
function percent(t) {
  const max = Number(t.maxMembers || 0)
  if (!max) return 0
  return Math.min(100, Math.round((Number(t.currentMembers || 0) / max) * 100))
}

async function load() {
  state.value = 'loading'
  try {
    const data = await getTeams({ courtId: courtId.value, date: date.value })
    list.value = Array.isArray(data) ? data : []
    state.value = list.value.length ? 'success' : 'empty'
  } catch (e) {
    errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
    state.value = 'error'
  }
}

function clearDate() {
  if (!date.value) return
  date.value = ''
  load()
}

function clearCourt() {
  courtId.value = ''
  load()
}

function goDetail(t) {
  uni.navigateTo({ url: `/pages/team/detail?id=${t.id}` })
}

function goVenue() {
  uni.switchTab({
    url: '/pages/venue/list',
    fail: () => uni.navigateTo({ url: '/pages/venue/list' })
  })
}

onShow(() => {
  if (!requireLogin()) return
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
</script>

<style scoped>
.filter-card {
  background-color: #ffffff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
}
.card-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2329;
}
.filter-tip {
  margin-right: 16rpx;
}
.clear {
  padding: 0 6rpx;
}
.court-filter {
  margin-top: 8rpx;
}
.team-card {
  background-color: #ffffff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}
.team-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1f2329;
  max-width: 400rpx;
}
.resv-line {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin-top: 14rpx;
}
.progress-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 18rpx;
}
.progress-bar {
  flex: 1;
  height: 14rpx;
  background-color: #f2f3f5;
  border-radius: 7rpx;
  overflow: hidden;
  margin-right: 16rpx;
}
.progress-inner {
  height: 14rpx;
  background-color: #1677ff;
  border-radius: 7rpx;
}
.progress-text {
  color: #4e5969;
}
.team-foot {
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f5f6f8;
}
</style>
