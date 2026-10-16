<template>
  <view class="state-box">
    <!-- 加载中 -->
    <template v-if="status === 'loading'">
      <view class="spinner"></view>
      <text class="state-text">{{ loadingText }}</text>
    </template>

    <!-- 错误态 -->
    <template v-else-if="status === 'error'">
      <text class="state-icon">⚠️</text>
      <text class="state-text">{{ errorText }}</text>
      <button class="btn btn-plain btn-sm" @click="$emit('retry')">重新加载</button>
    </template>

    <!-- 空态 -->
    <template v-else-if="status === 'empty'">
      <text class="state-icon">🗂</text>
      <text class="state-text">{{ emptyText }}</text>
      <button v-if="showEmptyAction" class="btn btn-plain btn-sm" @click="$emit('action')">
        {{ emptyActionText }}
      </button>
      <slot name="empty"></slot>
    </template>

    <!-- 正常内容 -->
    <slot v-else></slot>
  </view>
</template>

<script setup>
/**
 * 统一 loading / 空态 / 错误态 容器
 * status: loading | error | empty | success
 */
defineProps({
  status: { type: String, default: 'success' },
  loadingText: { type: String, default: '加载中…' },
  errorText: { type: String, default: '加载失败，请稍后重试' },
  emptyText: { type: String, default: '暂无数据' },
  emptyActionText: { type: String, default: '去逛逛' },
  showEmptyAction: { type: Boolean, default: false }
})
defineEmits(['retry', 'action'])
</script>
