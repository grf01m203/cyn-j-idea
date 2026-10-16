<template>
  <view v-if="modelValue" class="modal-mask" @click="onMask">
    <view class="modal-box" @click.stop>
      <view class="modal-head">
        <text class="modal-title">{{ title }}</text>
        <text class="modal-close" @click="close">✕</text>
      </view>
      <view class="modal-body">
        <slot></slot>
      </view>
      <view class="modal-foot" v-if="showFooter">
        <button class="btn btn-default modal-btn" @click="close">{{ cancelText }}</button>
        <button class="btn btn-primary modal-btn" :disabled="confirmDisabled" @click="$emit('confirm')">
          {{ confirmText }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
/**
 * 轻量弹窗（自写，无第三方 UI 库）
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '提示' },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' },
  showFooter: { type: Boolean, default: true },
  confirmDisabled: { type: Boolean, default: false },
  maskClosable: { type: Boolean, default: true }
})
const emit = defineEmits(['update:modelValue', 'confirm', 'close'])

function close() {
  emit('update:modelValue', false)
  emit('close')
}

function onMask() {
  if (props.maskClosable) close()
}
</script>

<style scoped>
.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.45);
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
}
.modal-box {
  width: 640rpx;
  background-color: #ffffff;
  border-radius: 20rpx;
  overflow: hidden;
}
.modal-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 28rpx 12rpx;
}
.modal-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1f2329;
}
.modal-close {
  font-size: 28rpx;
  color: #8a9099;
  padding: 0 8rpx;
}
.modal-body {
  padding: 12rpx 28rpx 24rpx;
  max-height: 900rpx;
  overflow-y: auto;
}
.modal-foot {
  display: flex;
  flex-direction: row;
  padding: 0 28rpx 28rpx;
}
.modal-btn {
  flex: 1;
}
.modal-btn + .modal-btn {
  margin-left: 20rpx;
}
</style>
