<template>
  <view class="state-box" :class="{ 'state-box--content': status === 'success' }">
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

<style scoped>
/*
 * 全局 .state-box 是 flex + align-items:center，用于把「加载/错误/空」占位内容居中。
 * 但成功态直接把业务卡片放进槽位时，flex 居中会把卡片收缩成内容宽度
 * （H5 上表现为一条很窄的竖列，桌面端尤其明显）。
 * 这里让内容态回到普通块级布局，宽度交给页面自身控制。
 */
.state-box--content {
  display: block;
  padding: 0;
}
</style>
