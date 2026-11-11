<template>
  <view class="register-page">
    <view class="reg-header">
      <view class="logo">CV</view>
      <text class="title">注册账号</text>
      <text class="subtitle">校园场馆预约与拼场组队系统</text>
    </view>

    <view class="reg-card">
      <view class="form-item">
        <text class="form-label">用户名</text>
        <input
          class="form-input"
          v-model="form.username"
          placeholder="4~20 位字母、数字或下划线"
          placeholder-class="ph"
          :disabled="loading"
          maxlength="20"
        />
      </view>
      <view class="form-item">
        <text class="form-label">学号</text>
        <input
          class="form-input"
          v-model="form.studentNo"
          placeholder="6~20 位字母或数字"
          placeholder-class="ph"
          :disabled="loading"
          maxlength="20"
        />
      </view>
      <view class="form-item">
        <text class="form-label">昵称</text>
        <input
          class="form-input"
          v-model="form.nickname"
          placeholder="展示在预约与拼场中的名字"
          placeholder-class="ph"
          :disabled="loading"
          maxlength="20"
        />
      </view>
      <view class="form-item">
        <text class="form-label">手机号</text>
        <input
          class="form-input"
          v-model="form.phone"
          type="number"
          placeholder="选填，11 位手机号"
          placeholder-class="ph"
          :disabled="loading"
          maxlength="11"
        />
      </view>
      <view class="form-item">
        <text class="form-label">密码</text>
        <input
          class="form-input"
          v-model="form.password"
          password
          placeholder="6~20 位"
          placeholder-class="ph"
          :disabled="loading"
          maxlength="20"
        />
      </view>
      <view class="form-item">
        <text class="form-label">确认密码</text>
        <input
          class="form-input"
          v-model="form.confirmPassword"
          password
          placeholder="再次输入密码"
          placeholder-class="ph"
          :disabled="loading"
          maxlength="20"
          @confirm="onSubmit"
        />
      </view>

      <view class="err-box" v-if="errorMsg">
        <text class="text-danger text-sm">{{ errorMsg }}</text>
      </view>

      <button class="btn btn-primary reg-btn" :disabled="loading" @click="onSubmit">
        {{ loading ? '注册中…' : '注 册' }}
      </button>

      <view class="back-row" @click="goLogin">
        <text class="text-muted">已有账号？返回登录</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/store/auth.js'
import { goHome } from '@/utils/nav.js'

const auth = useAuthStore()
const loading = ref(false)
const errorMsg = ref('')
const form = reactive({
  username: '',
  studentNo: '',
  nickname: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

function validate() {
  const u = (form.username || '').trim()
  const sno = (form.studentNo || '').trim()
  const nick = (form.nickname || '').trim()
  const tel = (form.phone || '').trim()
  if (!/^[A-Za-z0-9_]{4,20}$/.test(u)) return '用户名需为 4~20 位字母、数字或下划线'
  if (!/^[A-Za-z0-9]{6,20}$/.test(sno)) return '学号需为 6~20 位字母或数字'
  if (!nick) return '请填写昵称'
  if (nick.length > 20) return '昵称不能超过 20 个字符'
  if (tel && !/^1[3-9]\d{9}$/.test(tel)) return '手机号格式不正确'
  if (!form.password || form.password.length < 6 || form.password.length > 20) return '密码长度需为 6~20 位'
  if (form.password !== form.confirmPassword) return '两次输入的密码不一致'
  return ''
}

function goLogin() {
  uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/login/index' }) })
}

async function onSubmit() {
  if (loading.value) return
  const msg = validate()
  if (msg) {
    errorMsg.value = msg
    return
  }
  errorMsg.value = ''
  loading.value = true
  try {
    await auth.register({
      username: (form.username || '').trim(),
      studentNo: (form.studentNo || '').trim(),
      nickname: (form.nickname || '').trim(),
      phone: (form.phone || '').trim(),
      password: form.password
    })
    uni.showToast({ title: '注册成功', icon: 'success' })
    setTimeout(goHome, 500)
  } catch (e) {
    errorMsg.value = (e && e.message) || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #1677ff 0%, #4a95ff 26%, #f5f6f8 26%, #f5f6f8 100%);
  padding: 0 40rpx 60rpx;
}
.reg-header {
  padding: 100rpx 0 48rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.logo {
  width: 100rpx;
  height: 100rpx;
  border-radius: 24rpx;
  background-color: rgba(255, 255, 255, 0.22);
  color: #ffffff;
  font-size: 38rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20rpx;
}
.title {
  font-size: 40rpx;
  color: #ffffff;
  font-weight: 700;
}
.subtitle {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 10rpx;
}
.reg-card {
  background-color: #ffffff;
  border-radius: 20rpx;
  padding: 16rpx 32rpx 32rpx;
  box-shadow: 0 8rpx 24rpx rgba(22, 119, 255, 0.12);
}
.ph {
  color: #b3b8bf;
}
.err-box {
  padding: 12rpx 0 0;
}
.reg-btn {
  margin-top: 32rpx;
}
.back-row {
  margin-top: 24rpx;
  display: flex;
  justify-content: center;
}
</style>
