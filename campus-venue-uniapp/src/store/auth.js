/**
 * 登录态 / 用户信息 store（Pinia）
 */
import { defineStore } from 'pinia'
import { STORAGE_KEYS } from '@/config/index.js'
import { getToken, setToken, clearToken } from '@/utils/request.js'
import * as authApi from '@/api/auth.js'
import { closeNoticeSocket, startNoticeSocket } from '@/utils/ws.js'
import { useNoticeStore } from '@/store/notice.js'

function readUser() {
  try {
    const u = uni.getStorageSync(STORAGE_KEYS.user)
    if (!u) return null
    return typeof u === 'string' ? JSON.parse(u) : u
  } catch (e) {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    user: readUser(),
    /** /auth/me 完整资料 */
    profile: null,
    loading: false
  }),

  getters: {
    isLogin: (state) => !!state.token,
    nickname: (state) => (state.profile && state.profile.nickname) || (state.user && state.user.nickname) || '未登录',
    creditScore: (state) => {
      if (state.profile && state.profile.creditScore != null) return state.profile.creditScore
      if (state.user && state.user.creditScore != null) return state.user.creditScore
      return '-'
    },
    violateCount: (state) => (state.profile && state.profile.violateCount) || 0,
    blacklistUntil: (state) => (state.profile && state.profile.blacklistUntil) || null
  },

  actions: {
    persistUser(u) {
      this.user = u || null
      if (u) {
        uni.setStorageSync(STORAGE_KEYS.user, JSON.stringify(u))
      } else {
        uni.removeStorageSync(STORAGE_KEYS.user)
      }
    },

    /** 登录 */
    async login(username, password) {
      this.loading = true
      try {
        const data = await authApi.login({ username, password })
        this.token = data.token
        setToken(data.token)
        this.persistUser(data.user)
        // 登录后建立实时消息连接
        startNoticeSocket()
        const notice = useNoticeStore()
        notice.refreshUnread()
        return data
      } finally {
        this.loading = false
      }
    },

    /** 拉取个人资料 GET /auth/me */
    async fetchProfile() {
      const data = await authApi.getMe()
      this.profile = data
      return data
    },

    /** 退出登录 */
    async logout() {
      try {
        await authApi.logout()
      } catch (e) {
        /* 后端异常也要清理本地登录态 */
      }
      this.reset()
    },

    reset() {
      this.token = ''
      this.profile = null
      this.persistUser(null)
      clearToken()
      closeNoticeSocket()
      const notice = useNoticeStore()
      notice.reset()
    }
  }
})
