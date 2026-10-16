<template>
  <el-popover placement="bottom-end" :width="340" trigger="click" @show="onShow">
    <template #reference>
      <el-badge :value="noticeStore.unreadCount" :max="99" :hidden="!noticeStore.hasUnread" class="bell-badge">
        <el-icon :size="19" class="bell-icon"><Bell /></el-icon>
      </el-badge>
    </template>

    <div class="notice-panel">
      <div class="panel-header">
        <span>未读消息（{{ noticeStore.unreadCount }}）</span>
        <el-button link type="primary" size="small" :disabled="!noticeStore.hasUnread" @click="onReadAll">
          全部已读
        </el-button>
      </div>
      <el-scrollbar max-height="280px">
        <el-empty v-if="!recentList.length" description="暂无未读消息" :image-size="60" />
        <div
          v-for="item in recentList"
          :key="item.id"
          class="notice-item"
          @click="onRead(item)"
        >
          <div class="notice-title">
            <el-tag size="small" type="warning" effect="plain">{{ noticeTypeLabel(item.type) }}</el-tag>
            <span class="title-text">{{ item.title }}</span>
          </div>
          <div class="notice-content">{{ item.content }}</div>
          <div class="notice-time">{{ item.createTime }}</div>
        </div>
      </el-scrollbar>
      <div class="panel-footer">
        <el-button link type="primary" size="small" @click="goNotice">进入消息中心</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useNoticeStore } from '@/store/notice'
import { noticeTypeLabel } from '@/utils/format'

const noticeStore = useNoticeStore()
const router = useRouter()

const recentList = computed(() => noticeStore.items.filter((it) => !it.read && !it.readFlag).slice(0, 8))

async function onShow () {
  await noticeStore.loadList({ pageNum: 1, pageSize: 10 })
  await noticeStore.loadUnreadCount()
}

async function onRead (item) {
  try {
    await noticeStore.markRead(item.id)
    ElMessage.success('已标记为已读')
  } catch (e) {
    // 统一拦截已提示
  }
}

async function onReadAll () {
  try {
    await noticeStore.markAllRead()
    ElMessage.success('已全部标记为已读')
  } catch (e) {
    // 统一拦截已提示
  }
}

function goNotice () {
  router.push('/notice')
}
</script>

<style scoped>
.bell-badge {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
}

.bell-icon {
  color: #606266;
}

.notice-panel .panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #303133;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--cv-border);
}

.notice-item {
  padding: 8px 4px;
  border-bottom: 1px dashed #f0f0f0;
  cursor: pointer;
}

.notice-item:hover {
  background-color: #f7f9fc;
}

.notice-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.title-text {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.notice-content {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
  line-height: 1.5;
}

.notice-time {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 4px;
}

.panel-footer {
  text-align: center;
  padding-top: 6px;
  border-top: 1px solid var(--cv-border);
}
</style>
