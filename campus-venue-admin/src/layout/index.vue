<template>
  <el-container class="app-wrapper">
    <el-aside :width="collapsed ? '64px' : '210px'" class="app-aside">
      <div class="logo">
        <el-icon :size="22"><OfficeBuilding /></el-icon>
        <span v-show="!collapsed" class="logo-text">CampusVenue</span>
      </div>
      <el-scrollbar class="menu-scroll">
        <el-menu
          class="cv-menu"
          :default-active="activeMenu"
          :collapse="collapsed"
          :collapse-transition="false"
          background-color="#1f2733"
          text-color="#c0c4cc"
          active-text-color="#ffffff"
          router
        >
          <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
            <el-icon><component :is="item.meta.icon" /></el-icon>
            <template #title>{{ item.meta.title }}</template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <el-icon class="collapse-btn" :size="20" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-for="(item, idx) in breadcrumbs" :key="idx">
              {{ item }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <notice-bell />
          <el-divider direction="vertical" />
          <el-dropdown trigger="click" @command="onCommand">
            <span class="user-info">
              <el-avatar :size="28" class="user-avatar">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ userStore.nickname }}</span>
              <el-tag size="small" :type="isSysAdmin ? 'danger' : 'primary'">{{ roleText }}</el-tag>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon> 我的
                </el-dropdown-item>
                <el-dropdown-item command="notice">
                  <el-icon><Bell /></el-icon> 消息中心
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <keep-alive :include="[]">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import NoticeBell from '@/components/NoticeBell.vue'
import { menuRoutes } from '@/router'
import { useUserStore } from '@/store/user'
import { useNoticeStore } from '@/store/notice'
import { roleLabel } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const noticeStore = useNoticeStore()

const collapsed = ref(false)
const menus = menuRoutes.filter((r) => !r.meta?.hidden)

const activeMenu = computed(() => {
  // 详情/子页面通过 meta.activeMenu 指定高亮项
  return route.meta?.activeMenu || route.path
})

const breadcrumbs = computed(() => {
  const list = []
  route.matched.forEach((record) => {
    if (record.meta?.title && record.path !== '/') list.push(record.meta.title)
  })
  if (!list.length && route.meta?.title) list.push(route.meta.title)
  return list
})

const avatarText = computed(() => (userStore.nickname || 'A').slice(0, 1).toUpperCase())
const roleText = computed(() => roleLabel(userStore.role))
const isSysAdmin = computed(() => userStore.role === 'SYS_ADMIN')

async function onCommand (command) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'notice') {
    router.push('/notice')
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出登录？', '提示', {
        type: 'warning',
        confirmButtonText: '退出',
        cancelButtonText: '取消'
      })
    } catch (e) {
      return
    }
    await userStore.logout()
    noticeStore.reset()
    ElMessage.success('已退出登录')
    router.replace('/login')
  }
}

onMounted(() => {
  noticeStore.loadUnreadCount()
  noticeStore.startPolling(5000)
})

onBeforeUnmount(() => {
  noticeStore.stopPolling()
})
</script>

<style scoped>
.app-wrapper {
  height: 100vh;
}

.app-aside {
  background-color: var(--cv-sidebar-bg);
  transition: width 0.2s;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 18px;
  color: #fff;
  background-color: #171e27;
  flex-shrink: 0;
  white-space: nowrap;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.menu-scroll {
  flex: 1;
}

.app-header {
  height: 56px;
  background-color: #fff;
  border-bottom: 1px solid var(--cv-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.collapse-btn {
  cursor: pointer;
  color: #606266;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.user-avatar {
  background-color: var(--cv-primary);
  color: #fff;
  font-size: 13px;
}

.user-name {
  font-size: 14px;
  color: #303133;
}

.app-main {
  padding: 0;
  background-color: var(--cv-bg);
  overflow-y: auto;
}
</style>
