<template>
  <view class="unread-fab" @click="goNotice">
    <text class="fab-icon">🔔</text>
    <view class="fab-dot" v-if="notice.hasUnread">
      <text class="fab-dot-text">{{ notice.unread > 99 ? '99+' : notice.unread }}</text>
    </view>
  </view>
</template>

<script setup>
/**
 * 未读消息红点浮标（tabBar 页无自定义导航栏时使用）
 */
import { useNoticeStore } from '@/store/notice.js'

const notice = useNoticeStore()

function goNotice() {
  uni.switchTab({
    url: '/pages/notice/list',
    fail: () => uni.navigateTo({ url: '/pages/notice/list' })
  })
}
</script>

<style scoped>
.unread-fab {
  position: fixed;
  right: 28rpx;
  bottom: 180rpx;
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background-color: #ffffff;
  box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.14);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 90;
}
.fab-icon {
  font-size: 38rpx;
}
.fab-dot {
  position: absolute;
  top: -6rpx;
  right: -6rpx;
  min-width: 34rpx;
  height: 34rpx;
  padding: 0 8rpx;
  border-radius: 17rpx;
  background-color: #ff4d4f;
  display: flex;
  align-items: center;
  justify-content: center;
}
.fab-dot-text {
  color: #ffffff;
  font-size: 20rpx;
  line-height: 34rpx;
}
</style>
