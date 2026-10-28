<template>
  <div class="court-page">
    <page-header
      title="场地管理"
      icon="Grid"
      description="GET /api/v1/venues/{id}/courts · POST/PUT/DELETE /api/v1/admin/courts · 开放规则 GET /admin/open-rules?courtId= · PUT /admin/courts/{id}/rules"
    />

    <div class="page-container">
      <el-card shadow="never">
        <div class="search-bar">
          <el-select
            v-model="venueId"
            filterable
            clearable
            placeholder="请选择场馆"
            style="width: 260px"
            @change="onVenueChange"
          >
            <el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" />
          </el-select>
          <el-input
            v-model="keyword"
            placeholder="按场地名称/类型过滤"
            clearable
            style="width: 220px"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button :icon="Refresh" @click="loadCourts">刷新</el-button>
          <el-alert
            v-if="!venueId"
            class="inline-alert"
            title="场地列表接口按场馆维度提供（/venues/{id}/courts），请先选择场馆"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>

        <div class="table-toolbar">
          <span class="text-muted">
            当前场馆：{{ currentVenueName || '未选择' }}，共 {{ filteredList.length }} 个场地
          </span>
          <div>
            <el-button type="primary" :icon="Plus" :disabled="!venueId" @click="openCreate">新增场地</el-button>
          </div>
        </div>

        <el-table :data="filteredList" v-loading="loading" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="场地名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="type" label="类型" width="110" />
          <el-table-column prop="capacity" label="容纳人数" width="100" align="center" />
          <el-table-column label="每小时价格" width="120" align="right">
            <template #default="{ row }">￥{{ money(row.pricePerHour) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" size="small">
                {{ Number(row.status) === 1 ? '可用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openRules(row)">开放规则</el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="onDelete(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="venueId ? '该场馆暂无场地，点击「新增场地」创建' : '请先选择场馆'" :image-size="90" />
          </template>
        </el-table>
      </el-card>
    </div>

    <!-- 场馆/场地表单 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑场地' : '新增场地'"
      width="520px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px" class="dialog-form">
        <el-form-item label="所属场馆" prop="venueId">
          <el-select v-model="form.venueId" placeholder="请选择场馆" filterable style="width: 100%">
            <el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="场地名称" prop="name">
          <el-input v-model.trim="form.name" maxlength="80" placeholder="如 羽毛球 2 号场" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" allow-create filterable default-first-option placeholder="请选择或输入类型">
            <el-option v-for="t in COURT_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="容纳人数" prop="capacity">
          <el-input-number v-model="form.capacity" :min="1" :max="500" :step="1" />
        </el-form-item>
        <el-form-item label="每小时价格" prop="pricePerHour">
          <el-input-number v-model="form.pricePerHour" :min="0" :max="99999" :precision="2" :step="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 开放规则抽屉 -->
    <el-drawer v-model="rulesVisible" size="72%" :title="`开放规则 · ${currentCourt?.name || ''}`">
      <div v-loading="rulesLoading" class="rules-body">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="整体覆盖保存：PUT /api/v1/admin/courts/{id}/rules 会以本次提交的 rules 数组覆盖该场地全部规则，请确认后保存。"
          class="mb-8"
        />

        <div class="rules-toolbar">
          <el-button type="primary" :icon="Plus" @click="addRule">新增一行</el-button>
          <el-button :icon="MagicStick" @click="batchDialogVisible = true">批量生成一周规则</el-button>
          <el-button :icon="Refresh" @click="loadRules">重新加载</el-button>
          <span class="text-muted">共 {{ rules.length }} 条</span>
        </div>

        <el-table :data="rules" border size="small" empty-text="暂无规则，点击「新增一行」或「批量生成一周规则」">
          <el-table-column label="星期" width="120">
            <template #default="{ row }">
              <el-select v-model="row.weekday" size="small" placeholder="选择">
                <el-option
                  v-for="w in 7"
                  :key="w"
                  :label="weekdayLabel(w)"
                  :value="w"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="开放开始" width="140">
            <template #default="{ row }">
              <el-time-select
                v-model="row.startTime"
                size="small"
                start="06:00"
                step="00:30"
                end="23:30"
                placeholder="开始"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="开放结束" width="140">
            <template #default="{ row }">
              <el-time-select
                v-model="row.endTime"
                size="small"
                start="06:00"
                step="00:30"
                end="23:30"
                placeholder="结束"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="槽位粒度(分钟)" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.slotMinutes" size="small" :min="10" :max="120" :step="10" controls-position="right" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="单次最长(分钟)" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.maxDuration" size="small" :min="30" :max="720" :step="30" controls-position="right" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="每小时价格" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.price" size="small" :min="0" :max="99999" :precision="2" :step="10" controls-position="right" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button link type="danger" @click="rules.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="rules-footer">
          <el-button @click="rulesVisible = false">取消</el-button>
          <el-button type="primary" :loading="rulesSaving" @click="onSaveRules">保存（整体覆盖）</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 批量生成 -->
    <el-dialog v-model="batchDialogVisible" title="批量生成一周规则" width="480px">
      <el-form :model="batch" label-width="110px">
        <el-form-item label="开始时间">
          <el-time-select v-model="batch.startTime" start="06:00" step="00:30" end="23:00" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-time-select v-model="batch.endTime" start="06:30" step="00:30" end="23:30" style="width: 100%" />
        </el-form-item>
        <el-form-item label="槽位粒度">
          <el-input-number v-model="batch.slotMinutes" :min="10" :max="120" :step="10" />
        </el-form-item>
        <el-form-item label="单次最长">
          <el-input-number v-model="batch.maxDuration" :min="30" :max="720" :step="30" />
        </el-form-item>
        <el-form-item label="每小时价格">
          <el-input-number v-model="batch.price" :min="0" :max="99999" :precision="2" :step="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="applyBatch">生成（覆盖当前编辑内容）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  fetchVenues,
  fetchVenueCourts,
  createCourt,
  updateCourt,
  deleteCourt,
  fetchOpenRules,
  saveOpenRules
} from '@/api/venue'
import { COURT_TYPES, toHm, toMoney, toTimeValue, weekdayLabel } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const venues = ref([])
const venueId = ref(null)
const courts = ref([])
const keyword = ref('')
const loading = ref(false)

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, venueId: null, name: '', type: '通用', capacity: 1, pricePerHour: 0 })

const rulesVisible = ref(false)
const rulesLoading = ref(false)
const rulesSaving = ref(false)
const currentCourt = ref(null)
const rules = ref([])

const batchDialogVisible = ref(false)
const batch = reactive({ startTime: '08:00', endTime: '22:00', slotMinutes: 30, maxDuration: 240, price: 30 })

const money = toMoney
const hm = toHm

/** 表单校验规则（命名 formRules，避免与「开放规则」数据 rules 混淆） */
const formRules = {
  name: [
    { required: true, message: '请输入场地名称', trigger: 'blur' },
    { min: 2, max: 80, message: '长度 2~80 个字符', trigger: 'blur' }
  ],
  venueId: [{ required: true, message: '请选择所属场馆', trigger: 'change' }],
  type: [{ required: true, message: '请选择场地类型', trigger: 'change' }],
  capacity: [{ required: true, message: '请输入容纳人数', trigger: 'change' }],
  pricePerHour: [{ required: true, message: '请输入每小时价格', trigger: 'change' }]
}

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return courts.value
  return courts.value.filter(
    (c) =>
      String(c.name || '').toLowerCase().includes(kw) || String(c.type || '').toLowerCase().includes(kw)
  )
})

const currentVenueName = computed(() => venues.value.find((v) => v.id === venueId.value)?.name || '')

async function loadVenues () {
  try {
    const page = await fetchVenues({ pageNum: 1, pageSize: 100 })
    venues.value = page?.records || []
  } catch (e) {
    venues.value = []
  }
  // 支持从场馆管理页带参进入
  const fromQuery = Number(route.query.venueId)
  if (fromQuery && venues.value.some((v) => v.id === fromQuery)) {
    venueId.value = fromQuery
  } else if (!venueId.value && venues.value.length) {
    venueId.value = venues.value[0].id
  }
  if (venueId.value) loadCourts()
}

async function loadCourts () {
  if (!venueId.value) {
    courts.value = []
    return
  }
  loading.value = true
  try {
    const data = await fetchVenueCourts(venueId.value)
    courts.value = Array.isArray(data) ? data : []
  } catch (e) {
    courts.value = []
  } finally {
    loading.value = false
  }
}

function onVenueChange () {
  keyword.value = ''
  // 保持 URL 与选择一致，便于刷新/分享
  router.replace({ path: '/court', query: venueId.value ? { venueId: venueId.value } : {} })
  loadCourts()
}

function resetForm () {
  form.id = null
  form.venueId = venueId.value || null
  form.name = ''
  form.type = '通用'
  form.capacity = 1
  form.pricePerHour = 0
  formRef.value?.clearValidate()
}

function openCreate () {
  resetForm()
  dialogVisible.value = true
}

function openEdit (row) {
  resetForm()
  form.id = row.id
  form.venueId = row.venueId ?? venueId.value
  form.name = row.name || ''
  form.type = row.type || '通用'
  form.capacity = Number(row.capacity ?? 1)
  form.pricePerHour = Number(row.pricePerHour ?? 0)
  dialogVisible.value = true
}

async function onSubmit () {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  const payload = {
    venueId: form.venueId,
    name: form.name,
    type: form.type,
    capacity: form.capacity,
    pricePerHour: form.pricePerHour
  }
  submitting.value = true
  try {
    if (form.id) {
      await updateCourt(form.id, payload)
      ElMessage.success('场地已更新')
    } else {
      await createCourt(payload)
      ElMessage.success('场地已创建')
    }
    dialogVisible.value = false
    // 若新增时切换了场馆，同步切换列表
    if (form.venueId !== venueId.value) {
      venueId.value = form.venueId
      router.replace({ path: '/court', query: { venueId: venueId.value } })
    }
    loadCourts()
  } catch (e) {
    // 请求层已提示
  } finally {
    submitting.value = false
  }
}

async function onDelete (row) {
  try {
    await ElMessageBox.confirm(
      `确认删除场地「${row.name}」？删除前请先确认无未完成预约；若存在未完成预约，后端将拒绝删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteCourt(row.id)
    ElMessage.success('场地已删除')
    loadCourts()
  } catch (e) {
    // 请求层已提示
  }
}

/* ---------------- 开放规则 ---------------- */
function openRules (row) {
  currentCourt.value = row
  rulesVisible.value = true
  loadRules()
}

async function loadRules () {
  if (!currentCourt.value) return
  rulesLoading.value = true
  try {
    const data = await fetchOpenRules(currentCourt.value.id)
    rules.value = (Array.isArray(data) ? data : []).map((r) => ({
      weekday: Number(r.weekday),
      startTime: toHm(r.startTime, '08:00'),
      endTime: toHm(r.endTime, '22:00'),
      slotMinutes: Number(r.slotMinutes ?? 30),
      maxDuration: Number(r.maxDuration ?? 240),
      price: Number(r.price ?? 0)
    }))
  } catch (e) {
    rules.value = []
  } finally {
    rulesLoading.value = false
  }
}

function addRule () {
  rules.value.push({
    weekday: 1,
    startTime: '08:00',
    endTime: '22:00',
    slotMinutes: 30,
    maxDuration: 240,
    price: 30
  })
}

function applyBatch () {
  if (batch.endTime <= batch.startTime) {
    ElMessage.error('结束时间必须晚于开始时间')
    return
  }
  rules.value = Array.from({ length: 7 }, (_, i) => ({
    weekday: i + 1,
    startTime: batch.startTime,
    endTime: batch.endTime,
    slotMinutes: batch.slotMinutes,
    maxDuration: batch.maxDuration,
    price: batch.price
  }))
  batchDialogVisible.value = false
  ElMessage.success('已生成周一至周日 7 条规则，请检查后保存')
}

function validateRules () {
  if (!rules.value.length) {
    ElMessage.warning('请至少保留一条开放规则')
    return false
  }
  const seen = new Set()
  for (let i = 0; i < rules.value.length; i += 1) {
    const r = rules.value[i]
    const idx = i + 1
    if (!r.weekday) {
      ElMessage.error(`第 ${idx} 行：请选择星期`)
      return false
    }
    if (seen.has(r.weekday)) {
      ElMessage.error(`第 ${idx} 行：星期「${weekdayLabel(r.weekday)}」重复，同一场地每天只允许一条规则`)
      return false
    }
    seen.add(r.weekday)
    if (!r.startTime || !r.endTime) {
      ElMessage.error(`第 ${idx} 行：请选择开放时间段`)
      return false
    }
    if (r.endTime <= r.startTime) {
      ElMessage.error(`第 ${idx} 行：结束时间必须晚于开始时间`)
      return false
    }
    const spanMinutes = toMinutes(r.endTime) - toMinutes(r.startTime)
    if (!(r.slotMinutes > 0) || spanMinutes % r.slotMinutes !== 0) {
      ElMessage.error(`第 ${idx} 行：开放时长（${spanMinutes} 分钟）必须能被槽位粒度（${r.slotMinutes} 分钟）整除`)
      return false
    }
    if (r.maxDuration > spanMinutes) {
      ElMessage.error(`第 ${idx} 行：单次最长时长不能超过当天开放时长（${spanMinutes} 分钟）`)
      return false
    }
    if (r.price === null || r.price === undefined || r.price < 0) {
      ElMessage.error(`第 ${idx} 行：请输入有效的每小时价格`)
      return false
    }
  }
  return true
}

function toMinutes (t) {
  const [h, m] = String(t).split(':').map(Number)
  return h * 60 + m
}

async function onSaveRules () {
  if (!validateRules()) return
  try {
    await ElMessageBox.confirm(
      '保存将以当前表格内容整体覆盖该场地的全部开放规则，确认继续？',
      '整体覆盖确认',
      { type: 'warning', confirmButtonText: '确认覆盖', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  rulesSaving.value = true
  try {
    const payload = rules.value.map((r) => ({
      weekday: Number(r.weekday),
      startTime: toTimeValue(r.startTime),
      endTime: toTimeValue(r.endTime),
      slotMinutes: Number(r.slotMinutes),
      maxDuration: Number(r.maxDuration),
      price: Number(r.price)
    }))
    await saveOpenRules(currentCourt.value.id, payload)
    ElMessage.success('开放规则已保存')
    rulesVisible.value = false
  } catch (e) {
    // 请求层已提示
  } finally {
    rulesSaving.value = false
  }
}

watch(
  () => route.query.venueId,
  (val) => {
    const id = Number(val)
    if (id && id !== venueId.value && venues.value.some((v) => v.id === id)) {
      venueId.value = id
      loadCourts()
    }
  }
)

onMounted(loadVenues)
</script>

<style scoped>
.inline-alert {
  width: auto;
  padding: 4px 12px;
}

.rules-body {
  padding: 0 4px 60px;
}

.rules-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.rules-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
</style>
