<template>
  <view class="page">
    <!-- 顶部：未读统计 + 操作 -->
    <view class="head-bar">
      <view class="row">
        <text class="head-title">消息中心</text>
        <view class="unread-badge" v-if="notice.unread > 0">
          <text class="unread-text">{{ notice.unread > 99 ? '99+' : notice.unread }} 条未读</text>
        </view>
        <view class="ws-dot" :class="'ws-' + notice.wsState"></view>
      </view>
      <view class="row">
        <text class="head-op" @click="toggleOnlyUnread">{{ onlyUnread ? '全部消息' : '只看未读' }}</text>
        <text class="head-op" @click="onReadAll" v-if="notice.unread > 0">全部已读</text>
      </view>
    </view>

    <view class="page-body">
      <StateView
        :status="state"
        loading-text="正在加载消息…"
        :error-text="errorMsg"
        :empty-text="onlyUnread ? '没有未读消息' : '暂无消息'"
        empty-action-text="刷新"
        :show-empty-action="true"
        @retry="load(true)"
        @action="load(true)"
      >
        <view
          class="notice-card"
          v-for="n in list"
          :key="n.id"
          :class="{ unread: !isRead(n) }"
          @click="onOpen(n)"
        >
          <view class="row-between">
            <view class="row">
              <view class="dot" v-if="!isRead(n)"></view>
              <text class="notice-title ellipsis">{{ n.title || typeText(n.type) }}</text>
            </view>
            <text class="tag" :class="typeClass(n.type)">{{ typeText(n.type) }}</text>
          </view>
          <text class="notice-content">{{ n.content }}</text>
          <view class="row-between notice-foot">
            <text class="text-sm text-muted">{{ formatDateTime(n.createTime) }}</text>
            <text class="text-sm text-primary" v-if="!isRead(n)">点击标记已读并查看</text>
            <text class="text-sm text-muted" v-else>已读</text>
          </view>
        </view>

        <view class="load-more" v-if="list.length">
          <text v-if="loadingMore">加载中…</text>
          <text v-else-if="finished">— 已加载全部 {{ total }} 条 —</text>
          <text v-else @click="loadMore">点击加载更多</text>
        </view>
      </StateView>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import StateView from '@/components/StateView.vue'
import {
  getNotifications,
  readNotification,
  readAllNotifications,
  getUnreadCount
} from '@/api/notice.js'
import { PAGE_SIZE } from '@/config/index.js'
import { formatDateTime, toast } from '@/utils/date.js'
import { requireLogin, isLogin } from '@/utils/nav.js'
import { useNoticeStore } from '@/store/notice.js'

const notice = useNoticeStore()

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const onlyUnread = ref(false)
const state = ref('loading')
const errorMsg = ref('加载失败，请稍后重试')
const loadingMore = ref(false)
const finished = ref(false)

const TYPE_TEXT = {
  RESV_CREATED: '预约创建',
  RESV_CANCELLED: '预约取消',
  RESV_REMIND: '预约提醒',
  TEAM_INVITE: '拼场邀请',
  TEAM_FULL: '拼场满员',
  TEAM_DISSOLVED: '拼场解散',
  QUEUE_PROMOTED: '候补递补',
  CHECKIN_REMIND: '签到提醒',
  CREDIT_CHANGED: '信用变动',
  ADMIN_NOTICE: '系统公告'
}

function typeText(t) {
  return TYPE_TEXT[t] || t || '通知'
}
function typeClass(t) {
  if (t === 'QUEUE_PROMOTED' || t === 'RESV_REMIND' || t === 'CHECKIN_REMIND') return 'tag-orange'
  if (t === 'RESV_CANCELLED' || t === 'TEAM_DISSOLVED' || t === 'CREDIT_CHANGED') return 'tag-red'
  if (t === 'TEAM_FULL' || t === 'RESV_CREATED') return 'tag-blue'
  return 'tag-gray'
}
/** readFlag：兼容 boolean 与 0/1 */
function isRead(n) {
  return n.readFlag === true || Number(n.readFlag) === 1
}

async function load(reset = false) {
  if (reset) {
    pageNum.value = 1
    finished.value = false
    state.value = 'loading'
  }
  try {
    const data = await getNotifications({
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
      onlyUnread: onlyUnread.value ? 1 : ''
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

function toggleOnlyUnread() {
  onlyUnread.value = !onlyUnread.value
  load(true)
}

/** 同步未读数（服务端为准） */
async function syncUnread() {
  try {
    const data = await getUnreadCount()
    notice.setUnread((data && data.count) || 0)
  } catch (e) {
    /* 忽略 */
  }
}

/** 打开消息 -> 标记已读 + 展示详情 */
async function onOpen(n) {
  uni.showModal({
    title: n.title || typeText(n.type),
    content: n.content || '',
    showCancel: false,
    confirmText: '知道了'
  })
  if (isRead(n)) return
  try {
    await readNotification(n.id)
    // 本地更新
    n.readFlag = 1
    notice.setUnread(Math.max(0, notice.unread - 1))
    if (onlyUnread.value) {
      list.value = list.value.filter((x) => x.id !== n.id)
    }
  } catch (e) {
    /* 静默 */
  }
}

async function onReadAll() {
  try {
    await readAllNotifications()
    notice.clearUnread()
    toast('已全部标记为已读', 'success')
    load(true)
  } catch (e) {
    if (e && e.message) toast(e.message)
  }
}

onShow(() => {
  if (!requireLogin()) return
  load(true)
  syncUnread()
})

onPullDownRefresh(async () => {
  if (!isLogin()) {
    uni.stopPullDownRefresh()
    return
  }
  await load(true)
  await syncUnread()
  uni.stopPullDownRefresh()
})

onReachBottom(() => loadMore())
</script>

<style scoped>
.head-bar {
  background-color: #ffffff;
  padding: 24rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1rpx solid #f2f3f5;
}
.head-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2329;
}
.unread-badge {
  margin-left: 16rpx;
  background-color: #ffeceb;
  border-radius: 20rpx;
  padding: 4rpx 14rpx;
}
.unread-text {
  font-size: 22rpx;
  color: #ff4d4f;
}
.ws-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  margin-left: 12rpx;
  background-color: #c9cdd4;
}
.ws-open {
  background-color: #52c41a;
}
.ws-connecting,
.ws-reconnecting {
  background-color: #fa8c16;
}
.ws-close {
  background-color: #ff4d4f;
}
.head-op {
  font-size: 26rpx;
  color: #1677ff;
  margin-left: 24rpx;
}
.notice-card {
  background-color: #ffffff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}
.notice-card.unread {
  border-left: 6rpx solid #1677ff;
}
.dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background-color: #ff4d4f;
  margin-right: 12rpx;
}
.notice-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2329;
  max-width: 380rpx;
}
.notice-content {
  display: block;
  font-size: 26rpx;
  color: #4e5969;
  line-height: 1.6;
  margin-top: 14rpx;
}
.notice-foot {
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f5f6f8;
}
</style>
