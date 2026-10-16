<template>
  <div class="notice-page">
    <page-header
      title="消息中心"
      icon="Bell"
      description="GET /api/v1/notifications · GET /notifications/unread-count · POST /notifications/{id}/read · POST /notifications/read-all · 轮询 GET /notifications/poll?lastId="
    >
      <template #extra>
        <el-tag :type="noticeStore.hasUnread ? 'danger' : 'info'" effect="dark">
          未读 {{ noticeStore.unreadCount }}
        </el-tag>
        <el-button
          type="primary"
          :icon="Select"
          :disabled="!noticeStore.hasUnread"
          @click="onReadAll"
        >
          全部已读
        </el-button>
      </template>
    </page-header>

    <div class="page-container">
      <el-card shadow="never">
        <div class="search-bar">
          <el-radio-group v-model="onlyUnread" @change="onFilterChange">
            <el-radio-button :value="false">全部消息</el-radio-button>
            <el-radio-button :value="true">仅未读</el-radio-button>
          </el-radio-group>
          <el-button :icon="Refresh" @click="reload">刷新</el-button>
          <span class="text-muted">
            实时刷新：{{ polling ? '5 秒轮询中' : '已停止' }}（GET /notifications/poll?lastId={{
              noticeStore.lastId
            }}）
          </span>
          <el-switch v-model="polling" active-text="轮询" @change="togglePolling" />
        </div>

        <el-table :data="noticeStore.items" v-loading="noticeStore.loading" border stripe>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="isRead(row) ? 'info' : 'danger'">
                {{ isRead(row) ? '已读' : '未读' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="130">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ noticeTypeLabel(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
          <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
          <el-table-column label="关联业务" width="150">
            <template #default="{ row }">
              <span v-if="row.bizType || row.bizId" class="text-muted">
                {{ row.bizType || '-' }} #{{ row.bizId ?? '-' }}
              </span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="isRead(row)" @click="onRead(row)">标记已读</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="onlyUnread ? '暂无未读消息' : '暂无消息'" :image-size="90" />
          </template>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="noticeStore.total"
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
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Select } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { useNoticeStore } from '@/store/notice'
import { noticeTypeLabel } from '@/utils/format'

const noticeStore = useNoticeStore()

const pageNum = ref(1)
const pageSize = ref(10)
const onlyUnread = ref(false)
const polling = ref(true)

function isRead (row) {
  return row.read === true || Number(row.readFlag) === 1
}

async function reload () {
  await noticeStore.loadList({ pageNum: pageNum.value, pageSize: pageSize.value, onlyUnread: onlyUnread.value })
  await noticeStore.loadUnreadCount()
}

function onFilterChange () {
  pageNum.value = 1
  reload()
}

function onPageChange (page) {
  pageNum.value = page
  reload()
}

function onSizeChange (size) {
  pageSize.value = size
  pageNum.value = 1
  reload()
}

async function onRead (row) {
  try {
    await noticeStore.markRead(row.id)
    ElMessage.success('已标记为已读')
    if (onlyUnread.value) reload()
  } catch (e) {
    // 请求层已提示
  }
}

async function onReadAll () {
  try {
    await noticeStore.markAllRead()
    ElMessage.success('已全部标记为已读')
    reload()
  } catch (e) {
    // 请求层已提示
  }
}

function togglePolling (val) {
  if (val) {
    noticeStore.startPolling(5000)
  } else {
    noticeStore.stopPolling()
  }
}

onMounted(async () => {
  await reload()
  polling.value = true
  noticeStore.startPolling(5000)
})

onBeforeUnmount(() => {
  noticeStore.stopPolling()
})
</script>
