<template>
  <view class="login-page">
    <view class="login-header">
      <view class="logo">CV</view>
      <text class="title">CampusVenue</text>
      <text class="subtitle">校园场馆预约与拼场组队系统</text>
    </view>

    <view class="login-card">
      <view class="form-item">
        <text class="form-label">用户名</text>
        <input
          class="form-input"
          v-model="form.username"
          placeholder="请输入学号 / 用户名"
          placeholder-class="ph"
          :disabled="loading"
          @confirm="onSubmit"
        />
      </view>
      <view class="form-item">
        <text class="form-label">密码</text>
        <input
          class="form-input"
          v-model="form.password"
          password
          placeholder="请输入密码"
          placeholder-class="ph"
          :disabled="loading"
          @confirm="onSubmit"
        />
      </view>

      <!-- 错误态 -->
      <view class="err-box" v-if="errorMsg">
        <text class="text-danger text-sm">{{ errorMsg }}</text>
      </view>

      <button class="btn btn-primary login-btn" :disabled="loading || !canSubmit" @click="onSubmit">
        {{ loading ? '登录中…' : '登 录' }}
      </button>

      <view class="demo-box">
        <text class="text-muted">演示账号（点击快速填充）</text>
        <view class="demo-row">
          <view class="demo-chip" @click="fill('student', '123456')">student / 123456</view>
          <view class="demo-chip" @click="fill('student2', '123456')">student2 / 123456</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { useAuthStore } from '@/store/auth.js'
import { goHome } from '@/utils/nav.js'

const auth = useAuthStore()
const loading = ref(false)
const errorMsg = ref('')
const form = reactive({ username: '', password: '' })

const canSubmit = computed(() => !!form.username && !!form.password)

function fill(u, p) {
  form.username = u
  form.password = p
  errorMsg.value = ''
}

async function onSubmit() {
  if (loading.value) return
  if (!canSubmit.value) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  errorMsg.value = ''
  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(goHome, 400)
  } catch (e) {
    errorMsg.value = (e && e.message) || '登录失败，请检查账号密码或后端服务'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #1677ff 0%, #4a95ff 32%, #f5f6f8 32%, #f5f6f8 100%);
  padding: 0 40rpx;
}
.login-header {
  padding: 140rpx 0 60rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.logo {
  width: 120rpx;
  height: 120rpx;
  border-radius: 28rpx;
  background-color: rgba(255, 255, 255, 0.22);
  color: #ffffff;
  font-size: 44rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
}
.title {
  font-size: 44rpx;
  color: #ffffff;
  font-weight: 700;
}
.subtitle {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 12rpx;
}
.login-card {
  background-color: #ffffff;
  border-radius: 20rpx;
  padding: 24rpx 32rpx 32rpx;
  box-shadow: 0 8rpx 24rpx rgba(22, 119, 255, 0.12);
}
.ph {
  color: #b3b8bf;
}
.err-box {
  padding: 16rpx 0 0;
}
.login-btn {
  margin-top: 40rpx;
}
.demo-box {
  margin-top: 32rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #f2f3f5;
}
.demo-row {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  margin-top: 12rpx;
}
.demo-chip {
  background-color: #f2f3f5;
  border-radius: 8rpx;
  padding: 10rpx 18rpx;
  font-size: 24rpx;
  color: #4e5969;
  margin: 8rpx 16rpx 0 0;
}
</style>
