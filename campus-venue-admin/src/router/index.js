import { createRouter, createWebHashHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import Layout from '@/layout/index.vue'
import { useUserStore, ADMIN_ROLES } from '@/store/user'

/** 菜单顺序即此处的定义顺序；meta.hidden 的路由不进入左侧菜单 */
export const menuRoutes = [
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    meta: { title: '数据看板', icon: 'DataLine' }
  },
  {
    path: '/venue',
    name: 'Venue',
    component: () => import('@/views/venue/index.vue'),
    meta: { title: '场馆管理', icon: 'OfficeBuilding' }
  },
  {
    path: '/court',
    name: 'Court',
    component: () => import('@/views/court/index.vue'),
    meta: { title: '场地管理', icon: 'Grid' }
  },
  {
    path: '/reservation',
    name: 'Reservation',
    component: () => import('@/views/reservation/index.vue'),
    meta: { title: '预约管理', icon: 'Tickets' }
  },
  {
    path: '/team',
    name: 'Team',
    component: () => import('@/views/team/index.vue'),
    meta: { title: '拼场监控', icon: 'UserFilled' }
  },
  {
    path: '/queue',
    name: 'Queue',
    component: () => import('@/views/queue/index.vue'),
    meta: { title: '候补监控', icon: 'AlarmClock' }
  },
  {
    path: '/checkin',
    name: 'Checkin',
    component: () => import('@/views/checkin/index.vue'),
    meta: { title: '签到记录', icon: 'Checked' }
  },
  {
    path: '/user',
    name: 'User',
    component: () => import('@/views/user/index.vue'),
    meta: { title: '用户管理', icon: 'User' }
  },
  {
    path: '/notice',
    name: 'Notice',
    component: () => import('@/views/notice/index.vue'),
    meta: { title: '消息中心', icon: 'Bell' }
  },
  {
    path: '/announcement',
    name: 'Announcement',
    component: () => import('@/views/announcement/index.vue'),
    meta: { title: '公告管理', icon: 'Notification' }
  }
]

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      ...menuRoutes,
      {
        path: '/profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '我的', icon: 'User', hidden: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', public: true }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const title = to.meta?.title
  document.title = title ? `${title} · CampusVenue 管理控制台` : 'CampusVenue 管理控制台'

  if (to.meta?.public) {
    // 已登录时访问登录页直接回首页
    if (to.path === '/login' && userStore.isLogin && userStore.isAdmin) {
      next({ path: '/dashboard' })
      return
    }
    next()
    return
  }

  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  const needRoles = to.meta?.roles
  const role = userStore.role
  if (needRoles && needRoles.length && !needRoles.includes(role)) {
    ElMessage.error('无权限访问该页面')
    next({ path: '/dashboard' })
    return
  }

  if (!ADMIN_ROLES.includes(role)) {
    ElMessage.error('当前账号不是管理员，无法访问管理端')
    userStore.clearAuth()
    next({ path: '/login' })
    return
  }

  next()
})

export default router
