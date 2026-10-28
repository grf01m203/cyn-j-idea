<template>
  <div class="venue-page">
    <page-header
      title="场馆管理"
      icon="OfficeBuilding"
      description="GET /api/v1/venues · POST/PUT/DELETE /api/v1/admin/venues"
    />

    <div class="page-container">
      <el-card shadow="never">
        <div class="search-bar">
          <el-input
            v-model="query.keyword"
            placeholder="场馆名称 / 地址关键字"
            clearable
            style="width: 260px"
            @keyup.enter="onSearch"
            @clear="onSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
        </div>

        <div class="table-toolbar">
          <span class="text-muted">共 {{ total }} 个场馆</span>
          <div>
            <el-button :icon="Refresh" @click="loadList">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新增场馆</el-button>
          </div>
        </div>

        <data-table
          :data="list"
          :loading="loading"
          :total="total"
          :page-num="query.pageNum"
          :page-size="query.pageSize"
          empty-text="暂无场馆数据，点击右上角「新增场馆」创建"
          @update:page-num="(v) => (query.pageNum = v)"
          @update:page-size="(v) => (query.pageSize = v)"
          @pagination-change="loadList"
        >
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="场馆名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="address" label="地址" min-width="160" show-overflow-tooltip />
          <el-table-column label="开放时间" width="150">
            <template #default="{ row }">
              {{ hm(row.openTime) }} ~ {{ hm(row.closeTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="联系电话" width="130" />
          <el-table-column label="场地数" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.courtCount ?? 0 }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="goCourts(row)">场地</el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="onDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </data-table>
      </el-card>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑场馆' : '新增场馆'"
      width="560px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="dialog-form">
        <el-form-item label="场馆名称" prop="name">
          <el-input v-model.trim="form.name" maxlength="80" show-word-limit placeholder="如 东区体育馆" />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model.trim="form.address" maxlength="200" placeholder="如 校区东门内 50 米" />
        </el-form-item>
        <el-form-item label="开放时间" prop="openTime">
          <el-time-select
            v-model="form.openTime"
            start="06:00"
            step="00:30"
            end="23:00"
            placeholder="开始时间"
            style="width: 140px"
            @change="onOpenTimeChange"
          />
        </el-form-item>
        <el-form-item label="关闭时间" prop="closeTime">
          <el-time-select
            v-model="form.closeTime"
            start="06:00"
            step="00:30"
            end="23:30"
            placeholder="结束时间"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model.trim="form.phone" maxlength="20" placeholder="如 0571-88888888" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="场馆简介、入馆须知等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import DataTable from '@/components/DataTable.vue'
import { fetchVenues, createVenue, updateVenue, deleteVenue } from '@/api/venue'
import { toHm, toTimeValue } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const list = ref([])
const total = ref(0)
const formRef = ref(null)

const query = reactive({ keyword: '', pageNum: 1, pageSize: 10 })

const form = reactive({
  id: null,
  name: '',
  address: '',
  openTime: '08:00',
  closeTime: '22:00',
  phone: '',
  description: ''
})

const rules = {
  name: [
    { required: true, message: '请输入场馆名称', trigger: 'blur' },
    { min: 2, max: 80, message: '长度 2~80 个字符', trigger: 'blur' }
  ],
  openTime: [{ required: true, message: '请选择开放开始时间', trigger: 'change' }],
  closeTime: [
    { required: true, message: '请选择开放结束时间', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        if (!value || !form.openTime) return callback()
        if (value <= form.openTime) return callback(new Error('结束时间必须晚于开始时间'))
        callback()
      },
      trigger: 'change'
    }
  ],
  phone: [
    {
      validator: (rule, value, callback) => {
        if (!value) return callback()
        if (!/^[\d\-+() ]{6,20}$/.test(value)) return callback(new Error('电话格式不正确'))
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const hm = toHm

async function loadList () {
  loading.value = true
  try {
    const page = await fetchVenues({
      keyword: query.keyword || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = page?.records || []
    total.value = Number(page?.total ?? list.value.length)
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch () {
  query.pageNum = 1
  loadList()
}

function onReset () {
  query.keyword = ''
  query.pageNum = 1
  loadList()
}

function resetForm () {
  form.id = null
  form.name = ''
  form.address = ''
  form.openTime = '08:00'
  form.closeTime = '22:00'
  form.phone = ''
  form.description = ''
  formRef.value?.clearValidate()
}

/** 开放开始时间变化后，重新校验关闭时间，避免残留旧的校验结果 */
function onOpenTimeChange () {
  if (!form.closeTime) return
  formRef.value?.validateField('closeTime', () => {})
}

function openCreate () {
  resetForm()
  dialogVisible.value = true
}

function openEdit (row) {
  resetForm()
  form.id = row.id
  form.name = row.name || ''
  form.address = row.address || ''
  form.openTime = toHm(row.openTime, '08:00')
  form.closeTime = toHm(row.closeTime, '22:00')
  form.phone = row.phone || ''
  form.description = row.description || ''
  dialogVisible.value = true
}

async function onSubmit () {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  const payload = {
    name: form.name,
    address: form.address,
    openTime: toTimeValue(form.openTime),
    closeTime: toTimeValue(form.closeTime),
    phone: form.phone,
    description: form.description
  }
  submitting.value = true
  try {
    if (form.id) {
      await updateVenue(form.id, payload)
      ElMessage.success('场馆已更新')
    } else {
      await createVenue(payload)
      ElMessage.success('场馆已创建')
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    // 请求层已提示
  } finally {
    submitting.value = false
  }
}

async function onDelete (row) {
  try {
    await ElMessageBox.confirm(
      `确认删除场馆「${row.name}」？删除前请先删除其下场地；若场馆下仍有场地或预约，后端将拒绝删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteVenue(row.id)
    ElMessage.success('场馆已删除')
    if (list.value.length === 1 && query.pageNum > 1) query.pageNum -= 1
    loadList()
  } catch (e) {
    // 请求层已提示
  }
}

function goCourts (row) {
  router.push({ path: '/court', query: { venueId: row.id, venueName: row.name } })
}

onMounted(loadList)
</script>
