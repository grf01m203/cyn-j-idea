<template>
  <div class="settings-page">
    <page-header
      title="系统设置"
      icon="Setting"
      description="GET/PUT /api/v1/admin/configs/checkin-window（保存后立即生效，无需重启）"
    />

    <div class="page-container">
      <el-card shadow="never" class="mb-16">
        <template #header>
          <div class="card-head">
            <span class="card-title">签到时间设置</span>
            <el-tag size="small" type="success">即时生效</el-tag>
          </div>
        </template>

        <el-form :model="form" label-width="150px" v-loading="loading" style="max-width: 640px">
          <el-form-item label="签到窗口（分钟）">
            <el-input-number
              v-model="form.windowMinutes"
              :min="form.minMinutes || 1"
              :max="form.maxMinutes || 1440"
              :step="5"
              step-strictly
              controls-position="right"
            />
            <div class="hint">
              预约开始时间前 / 后各 {{ form.windowMinutes || 0 }} 分钟允许生成签到码与核销；范围
              {{ form.minMinutes || 1 }} ~ {{ form.maxMinutes || 1440 }} 分钟。
            </div>
          </el-form-item>

          <el-form-item label="生效方式">
            <el-text type="info">保存后立即写入 sys_config 并刷新缓存，学生端倒计时与签到窗口同步更新。</el-text>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :icon="Check" :loading="saving" @click="onSave">保存设置</el-button>
            <el-button :icon="RefreshLeft" @click="load">重置为当前值</el-button>
          </el-form-item>
        </el-form>

        <el-alert type="info" :closable="false" show-icon class="mb-8">
          <template #title>
            当前配置：{{ form.key || 'checkin.window.minutes' }} = {{ savedMinutes }} 分钟（默认
            {{ form.defaultMinutes || 15 }} 分钟）
          </template>
          <div>{{ form.desc }}</div>
        </el-alert>
      </el-card>

      <el-card shadow="never">
        <template #header><span class="card-title">相关固定规则（只读）</span></template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="签到码有效期">60 秒（过期需重新生成）</el-descriptions-item>
          <el-descriptions-item label="签到码形式">6 位数字，一次性使用</el-descriptions-item>
          <el-descriptions-item label="失败锁定">同一登录用户 10 分钟内连续输错 5 次，锁定 10 分钟（错误码 30011）</el-descriptions-item>
          <el-descriptions-item label="核销渠道">扫码 / 手工输入同一接口，记录渠道 QRCODE</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, RefreshLeft } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { getCheckinWindow, updateCheckinWindow } from '@/api/settings'

const loading = ref(false)
const saving = ref(false)
const savedMinutes = ref(15)

const form = reactive({
  key: 'checkin.window.minutes',
  windowMinutes: 15,
  defaultMinutes: 15,
  minMinutes: 1,
  maxMinutes: 1440,
  desc: ''
})

function fill (data) {
  if (!data) return
  form.key = data.key || form.key
  form.minMinutes = data.minMinutes || form.minMinutes
  form.maxMinutes = data.maxMinutes || form.maxMinutes
  form.defaultMinutes = data.defaultMinutes || form.defaultMinutes
  form.desc = data.desc || form.desc
  form.windowMinutes = data.windowMinutes || form.defaultMinutes
  savedMinutes.value = form.windowMinutes
}

async function load () {
  loading.value = true
  try {
    const data = await getCheckinWindow()
    fill(data)
  } catch (e) {
    // 请求层已提示
  } finally {
    loading.value = false
  }
}

async function onSave () {
  const minutes = Number(form.windowMinutes)
  if (!Number.isInteger(minutes) || minutes < form.minMinutes || minutes > form.maxMinutes) {
    ElMessage.warning(`签到窗口需为 ${form.minMinutes}~${form.maxMinutes} 之间的整数分钟`)
    return
  }
  saving.value = true
  try {
    const data = await updateCheckinWindow(minutes)
    fill(data)
    ElMessage.success(`已保存：签到窗口 ${savedMinutes.value} 分钟（立即生效）`)
  } catch (e) {
    // 请求层已提示
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.settings-page {
  min-height: 100%;
}
.page-container {
  padding: 16px 20px 24px;
}
.mb-16 {
  margin-bottom: 16px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-title {
  font-weight: 600;
  font-size: 15px;
}
.hint {
  margin-top: 6px;
  font-size: 12px;
  color: #8a9099;
  line-height: 1.6;
}
</style>
