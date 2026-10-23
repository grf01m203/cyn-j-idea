<template>
  <view class="page">
    <!-- 用户信息卡 -->
    <view class="profile">
      <view class="avatar-wrap" @click="chooseAvatar">
        <image v-if="avatarUrl" class="avatar-img" :src="avatarUrl" mode="aspectFill" />
        <view v-else class="avatar">{{ (profile.nickname || auth.nickname || 'U').slice(0, 1) }}</view>
        <text class="avatar-edit" :class="{ uploading }">{{ uploading ? '…' : '📷' }}</text>
      </view>
      <view class="col flex-1">
        <view class="row">
          <text class="name">{{ profile.nickname || auth.nickname }}</text>
          <text class="tag tag-blue role-tag">{{ roleText(profile.role) }}</text>
        </view>
        <text class="text-sm sub">{{ profile.username || '-' }} · {{ profile.studentNo || '未绑定学号' }}</text>
        <text class="text-sm sub" v-if="profile.phone">📱 {{ profile.phone }}</text>
      </view>
    </view>

    <view class="page-body">
      <!-- 信用分 -->
      <StateView
        :status="state"
        loading-text="正在加载个人信息…"
        :error-text="errorMsg"
        empty-text="暂无个人信息"
        @retry="loadProfile"
      >
        <view class="card">
          <text class="card-title">信用信息</text>
          <view class="credit-row">
            <view class="credit-block">
              <text class="credit-num" :class="creditClass">{{ auth.creditScore }}</text>
              <text class="text-sm text-muted">信用分</text>
            </view>
            <view class="credit-block">
              <text class="credit-num">{{ auth.violateCount }}</text>
              <text class="text-sm text-muted">违约次数</text>
            </view>
            <view class="credit-block">
              <text class="credit-num small" :class="{ 'text-danger': !!auth.blacklistUntil }">
                {{ auth.blacklistUntil ? shortDate(auth.blacklistUntil) : '正常' }}
              </text>
              <text class="text-sm text-muted">黑名单截止</text>
            </view>
          </view>
          <view class="warn-box" v-if="auth.blacklistUntil">
            <text class="text-sm text-danger">
              你当前处于黑名单限制期，截止 {{ formatDateTime(auth.blacklistUntil) }}，期间无法预约场馆。
            </text>
          </view>
        </view>

        <!-- 功能入口 -->
        <view class="card menu-card">
          <view class="menu-item" v-for="m in menus" :key="m.url" @click="go(m.url)">
            <text class="menu-icon">{{ m.icon }}</text>
            <text class="menu-label">{{ m.label }}</text>
            <text class="menu-arrow" v-if="m.badge">›</text>
            <view class="menu-badge" v-if="m.badge">
              <text class="menu-badge-text">{{ notice.unread > 99 ? '99+' : notice.unread }}</text>
            </view>
            <text class="menu-arrow">›</text>
          </view>
        </view>

        <!-- 信用流水 -->
        <view class="card">
          <view class="row-between">
            <text class="card-title">信用流水</text>
            <text class="text-sm text-primary" @click="loadLogs">刷新</text>
          </view>
          <StateView
            :status="logState"
            loading-text="正在加载信用流水…"
            :error-text="logError"
            empty-text="暂无信用变动记录"
            @retry="loadLogs"
          >
            <view class="log-item" v-for="l in logs" :key="l.id">
              <view class="col flex-1">
                <text class="log-reason">{{ l.reason || '信用变动' }}</text>
                <text class="text-sm text-muted">{{ formatDateTime(l.createTime) }}</text>
              </view>
              <view class="col log-right">
                <text class="log-change" :class="Number(l.changeValue) >= 0 ? 'text-success' : 'text-danger'">
                  {{ Number(l.changeValue) > 0 ? '+' : '' }}{{ l.changeValue }}
                </text>
                <text class="text-sm text-muted">→ {{ l.afterScore }}</text>
              </view>
            </view>
            <view class="load-more" v-if="logs.length && !logFinished">
              <text @click="loadMoreLogs">点击加载更多</text>
            </view>
          </StateView>
        </view>
      </StateView>

      <button class="btn btn-danger logout-btn" @click="onLogout">退出登录</button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import StateView from '@/components/StateView.vue'
import { useAuthStore } from '@/store/auth.js'
import { useNoticeStore } from '@/store/notice.js'
import { getCreditLogs } from '@/api/credit.js'
import { updateAvatar as updateAvatarApi } from '@/api/auth.js'
import { API_PREFIX, PAGE_SIZE, REQUEST_BASE } from '@/config/index.js'
import { getToken } from '@/utils/request.js'
import { formatDateTime, toast } from '@/utils/date.js'
import { requireLogin } from '@/utils/nav.js'

const auth = useAuthStore()
const notice = useNoticeStore()

const state = ref('loading')
const errorMsg = ref('加载失败，请稍后重试')
const logs = ref([])
const logState = ref('loading')
const logError = ref('加载失败，请稍后重试')
const logPage = ref(1)
const logTotal = ref(0)
const logFinished = ref(false)

const profile = computed(() => auth.profile || auth.user || {})
const avatarUrl = computed(() => profile.value.avatar || (auth.user && auth.user.avatar) || '')
const uploading = ref(false)
const creditClass = computed(() => {
  const s = Number(auth.creditScore)
  if (isNaN(s)) return ''
  if (s >= 90) return 'text-success'
  if (s >= 60) return 'text-primary'
  return 'text-danger'
})

const menus = computed(() => [
  { icon: '📋', label: '我的预约', url: '/pages/reservation/list', badge: false },
  { icon: '🤝', label: '拼场广场', url: '/pages/team/list', badge: false },
  { icon: '⏳', label: '我的候补', url: '/pages/queue/list', badge: false },
  { icon: '🔔', label: '消息中心', url: '/pages/notice/list', badge: notice.unread > 0 },
  { icon: '🏟', label: '场馆列表', url: '/pages/venue/list', badge: false }
])

function chooseAvatar() {
  if (uploading.value) return
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      const filePath = res.tempFilePaths && res.tempFilePaths[0]
      if (filePath) doUploadAvatar(filePath)
    },
    fail: () => {}
  })
}

/** 上传头像：uni.uploadFile -> 后端 /api/v1/files/upload（自动走 OSS）-> PUT /auth/avatar */
function doUploadAvatar(filePath) {
  uploading.value = true
  uni.showLoading({ title: '上传中', mask: true })
  uni.uploadFile({
    url: `${REQUEST_BASE}${API_PREFIX}/files/upload?dir=avatar`,
    filePath,
    name: 'file',
    header: { Authorization: getToken() },
    success: async (res) => {
      let body = {}
      try {
        body = JSON.parse(res.data)
      } catch (e) {
        body = {}
      }
      if (body.code !== 0) {
        toast(body.message || '头像上传失败')
        return
      }
      try {
        await updateAvatarApi(body.data.url)
        await auth.fetchProfile()
        toast('头像已更新', 'success')
      } catch (e) {
        toast('头像保存失败')
      }
    },
    fail: () => toast('上传失败，请检查网络'),
    complete: () => {
      uploading.value = false
      uni.hideLoading()
    }
  })
}

function roleText(r) {  if (r === 'STUDENT') return '学生'
  if (r === 'VENUE_ADMIN') return '场馆管理员'
  if (r === 'SYS_ADMIN') return '系统管理员'
  return r || '学生'
}

function shortDate(v) {
  const s = String(v)
  const m = s.match(/(\d{4}-\d{2}-\d{2})/)
  return m ? m[1] : s
}

async function loadProfile() {
  state.value = 'loading'
  try {
    await auth.fetchProfile()
    state.value = auth.profile ? 'success' : 'empty'
  } catch (e) {
    errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
    state.value = 'error'
  }
}

async function loadLogs(reset = true) {
  if (reset) {
    logPage.value = 1
    logFinished.value = false
    logState.value = 'loading'
  }
  try {
    const data = await getCreditLogs({ pageNum: logPage.value, pageSize: PAGE_SIZE })
    const records = (data && data.records) || []
    logTotal.value = (data && data.total) || 0
    logs.value = reset ? records : logs.value.concat(records)
    logFinished.value = logs.value.length >= logTotal.value || records.length === 0
    logState.value = logs.value.length ? 'success' : 'empty'
  } catch (e) {
    logError.value = (e && e.message) || '加载失败，请稍后重试'
    if (reset || !logs.value.length) logState.value = 'error'
  }
}

function loadMoreLogs() {
  if (logFinished.value) return
  logPage.value += 1
  loadLogs(false)
}

function go(url) {
  if (url === '/pages/venue/list' || url === '/pages/reservation/list' || url === '/pages/team/list' || url === '/pages/notice/list') {
    uni.switchTab({
      url,
      fail: () => uni.navigateTo({ url })
    })
    return
  }
  uni.navigateTo({ url })
}

function onLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确认退出当前账号？',
    success: async (res) => {
      if (!res.confirm) return
      await auth.logout()
      toast('已退出登录', 'success')
      setTimeout(() => uni.reLaunch({ url: '/pages/login/index' }), 500)
    }
  })
}

onShow(() => {
  if (!requireLogin()) return
  loadProfile()
  loadLogs(true)
  notice.refreshUnread()
})

onReachBottom(() => loadMoreLogs())
</script>

<style scoped>
.profile {
  background: linear-gradient(135deg, #1677ff 0%, #4791ff 100%);
  padding: 48rpx 32rpx 56rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.avatar-wrap {
  position: relative;
  margin-right: 28rpx;
  width: 120rpx;
  height: 120rpx;
}
.avatar-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.25);
}
.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.25);
  color: #ffffff;
  font-size: 48rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-edit {
  position: absolute;
  right: -6rpx;
  bottom: -6rpx;
  width: 44rpx;
  height: 44rpx;
  line-height: 44rpx;
  text-align: center;
  font-size: 22rpx;
  background-color: #ffffff;
  border-radius: 50%;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.15);
}
.avatar-edit.uploading {
  color: #1677ff;
}
.name {
  font-size: 38rpx;
  font-weight: 700;
  color: #ffffff;
}
.role-tag {
  margin-left: 16rpx;
  background-color: rgba(255, 255, 255, 0.24);
  color: #ffffff;
}
.sub {
  color: rgba(255, 255, 255, 0.88);
  margin-top: 8rpx;
}
.credit-row {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  margin-top: 16rpx;
}
.credit-block {
  flex: 1;
  min-width: 160rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 12rpx;
}
.credit-num {
  font-size: 46rpx;
  font-weight: 700;
  color: #1f2329;
  margin-bottom: 8rpx;
}
.credit-num.small {
  font-size: 28rpx;
  line-height: 58rpx;
}
.warn-box {
  margin-top: 18rpx;
  padding: 16rpx;
  background-color: #fff1f0;
  border-radius: 10rpx;
}
.menu-card {
  padding: 0 24rpx;
}
.menu-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  height: 100rpx;
  border-bottom: 1rpx solid #f5f6f8;
}
.menu-item:last-child {
  border-bottom: none;
}
.menu-icon {
  font-size: 34rpx;
  margin-right: 20rpx;
}
.menu-label {
  flex: 1;
  font-size: 30rpx;
  color: #1f2329;
}
.menu-badge {
  background-color: #ff4d4f;
  border-radius: 18rpx;
  padding: 2rpx 14rpx;
  margin-right: 12rpx;
}
.menu-badge-text {
  color: #ffffff;
  font-size: 22rpx;
}
.menu-arrow {
  font-size: 30rpx;
  color: #c9cdd4;
}
.card-title {
  display: block;
  margin-bottom: 12rpx;
}
.log-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #f5f6f8;
}
.log-item:last-child {
  border-bottom: none;
}
.log-reason {
  font-size: 28rpx;
  color: #1f2329;
}
.log-right {
  align-items: flex-end;
}
.log-change {
  font-size: 32rpx;
  font-weight: 700;
}
.logout-btn {
  margin-top: 20rpx;
}
</style>
