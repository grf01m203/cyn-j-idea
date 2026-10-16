<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-brand">
        <el-icon :size="34"><OfficeBuilding /></el-icon>
        <h1>CampusVenue 管理控制台</h1>
        <p>校园场馆预约与拼场组队系统 · 管理端</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" clearable>
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password>
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="w-full" :loading="loading" @click="onSubmit">
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert
        title="仅 VENUE_ADMIN / SYS_ADMIN 角色可登录管理端"
        type="info"
        :closable="false"
        show-icon
        class="mb-8"
      />

      <div class="demo-title">演示账号（点击填充）</div>
      <div class="demo-accounts">
        <el-tag class="demo-tag" @click="fill('admin', '123456')">admin / 123456（场馆管理员）</el-tag>
        <el-tag class="demo-tag" type="danger" @click="fill('sysadmin', '123456')">
          sysadmin / 123456（系统管理员）
        </el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6~32 位', trigger: 'blur' }
  ]
}

function fill (username, password) {
  form.username = username
  form.password = password
}

async function onSubmit () {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    const user = await userStore.login({ username: form.username, password: form.password })
    ElMessage.success(`登录成功，欢迎 ${user.nickname || user.username}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    router.replace(redirect && redirect !== '/login' ? redirect : '/dashboard')
  } catch (e) {
    // 角色不符需要额外提示（请求层已提示网络/业务错误）
    if (e?.code === 'ROLE_NOT_ALLOWED') {
      ElMessage.error(e.message)
    } else if (e?.message && !e.response) {
      ElMessage.error(e.message)
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f6feb 0%, #16324f 60%, #0f1b26 100%);
}

.login-box {
  width: 420px;
  max-width: 92vw;
  background-color: #fff;
  border-radius: 10px;
  padding: 32px 32px 24px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.25);
}

.login-brand {
  text-align: center;
  margin-bottom: 22px;
  color: var(--cv-primary);
}

.login-brand h1 {
  font-size: 19px;
  margin: 10px 0 6px;
  color: #1f2733;
}

.login-brand p {
  font-size: 12px;
  color: var(--cv-text-muted);
  margin: 0;
}

.demo-title {
  text-align: center;
  font-size: 12px;
  color: var(--cv-text-muted);
  margin: 14px 0 10px;
  position: relative;
}

.demo-title::before,
.demo-title::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 30%;
  height: 1px;
  background-color: var(--cv-border);
}

.demo-title::before {
  left: 0;
}

.demo-title::after {
  right: 0;
}

.demo-accounts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.demo-tag {
  cursor: pointer;
}
</style>
