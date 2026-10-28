<template>
  <div class="announcement-page">
    <page-header
      title="公告管理"
      icon="Notification"
      description="POST /api/v1/admin/announcements · GET /api/v1/admin/announcements · DELETE /api/v1/admin/announcements/{id}"
    />

    <div class="page-container">
      <el-card shadow="never" class="page-card">
        <template #header>
          <div class="card-header"><span>发布公告</span></div>
        </template>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="80px"
          class="publish-form"
          @submit.prevent
        >
          <el-form-item label="标题" prop="title">
            <el-input
              v-model.trim="form.title"
              maxlength="50"
              show-word-limit
              clearable
              placeholder="请输入公告标题（不超过 50 字）"
            />
          </el-form-item>
          <el-form-item label="内容" prop="content">
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="4"
              maxlength="2000"
              show-word-limit
              placeholder="请输入公告内容（不超过 2000 字），发布后将推送给全部启用用户"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Promotion" :loading="publishing" @click="onPublish">
              发布公告
            </el-button>
            <el-button :icon="RefreshLeft" @click="onResetForm">清空</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>已发布公告</span>
            <el-button size="small" :icon="Refresh" :loading="loading" @click="loadList">刷新</el-button>
          </div>
        </template>

        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="content" label="内容" min-width="320" show-overflow-tooltip />
          <el-table-column prop="publishTime" label="发布时间" width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" @click="onRevoke(row)">撤销</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无已发布公告" :image-size="90" />
          </template>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            :current-page="pageNum"
            :page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Promotion, Refresh, RefreshLeft } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { createAnnouncement, fetchAnnouncements, deleteAnnouncement } from '@/api/announcement'

const formRef = ref(null)
const form = reactive({ title: '', content: '' })
const publishing = ref(false)

const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const rules = {
  title: [
    { required: true, message: '请输入公告标题', trigger: 'blur' },
    { max: 50, message: '标题不能超过 50 个字符', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入公告内容', trigger: 'blur' },
    { max: 2000, message: '内容不能超过 2000 个字符', trigger: 'blur' }
  ]
}

async function loadList () {
  loading.value = true
  try {
    const page = await fetchAnnouncements({ pageNum: pageNum.value, pageSize: pageSize.value })
    list.value = page?.records || []
    total.value = Number(page?.total ?? list.value.length)
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onPageChange (page) {
  pageNum.value = page
  loadList()
}

function onSizeChange (size) {
  pageSize.value = size
  pageNum.value = 1
  loadList()
}

function onResetForm () {
  form.title = ''
  form.content = ''
  formRef.value?.clearValidate()
}

async function onPublish () {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  publishing.value = true
  try {
    const res = await createAnnouncement({ title: form.title, content: form.content })
    const count = Number(res?.receiverCount ?? 0)
    ElMessage.success(`已发布，共推送给 ${count} 位用户`)
    onResetForm()
    pageNum.value = 1
    loadList()
  } catch (e) {
    // 请求层已用 ElMessage 展示后端 message（如标题/内容超长）
  } finally {
    publishing.value = false
  }
}

async function onRevoke (row) {
  try {
    await ElMessageBox.confirm(
      `确认撤销公告「${row.title}」？撤销后用户消息中心中的该公告也会一并删除，且不可恢复。`,
      '撤销确认',
      {
        type: 'warning',
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch (e) {
    return
  }
  try {
    await deleteAnnouncement(row.id)
    ElMessage.success('公告已撤销')
    // 当前页删空后自动回退一页
    if (list.value.length === 1 && pageNum.value > 1) pageNum.value -= 1
    loadList()
  } catch (e) {
    // 请求层已提示
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-weight: 600;
}

.publish-form {
  max-width: 820px;
}
</style>
