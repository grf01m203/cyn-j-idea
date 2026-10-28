import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, getMe } from '@/api/auth'
import { getItem, setItem, removeItem } from '@/utils/storage'
import { TOKEN_KEY, USER_KEY } from '@/utils/request'

/** 管理端允许登录的角色（契约 §0：管理端接口要求 VENUE_ADMIN 或 SYS_ADMIN） */
export const ADMIN_ROLES = ['VENUE_ADMIN', 'SYS_ADMIN']

function readCachedUser () {
  const raw = getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getItem(TOKEN_KEY) || '',
    userInfo: readCachedUser()
  }),
  getters: {
    isLogin: (state) => !!state.token,
    role: (state) => state.userInfo?.role || '',
    isAdmin: (state) => ADMIN_ROLES.includes(state.userInfo?.role),
    nickname: (state) => state.userInfo?.nickname || state.userInfo?.username || '管理员',
    avatar: (state) => state.userInfo?.avatar || '',
    userId: (state) => state.userInfo?.id
  },
  actions: {
    /** 登录：成功后校验角色必须是 VENUE_ADMIN / SYS_ADMIN */
    async login (payload) {
      const data = await loginApi(payload)
      const token = data?.token
      const user = data?.user || null
      if (!token) {
        throw new Error('登录响应缺少 token，请检查后端契约实现')
      }
      if (!user) {
        throw new Error('登录响应缺少 user 信息，请检查后端契约实现')
      }
      if (!ADMIN_ROLES.includes(user.role)) {
        // 非管理端角色：不写入登录态，直接拒绝
        this.clearAuth()
        const err = new Error('该账号不是管理员（需 VENUE_ADMIN 或 SYS_ADMIN），禁止登录管理端')
        err.code = 'ROLE_NOT_ALLOWED'
        throw err
      }
      this.token = token
      this.userInfo = user
      setItem(TOKEN_KEY, token)
      setItem(USER_KEY, JSON.stringify(user))
      return user
    },

    /** 拉取当前登录人完整信息（契约 §1 GET /auth/me） */
    async fetchProfile () {
      const me = await getMe()
      if (me) {
        this.userInfo = { ...(this.userInfo || {}), ...me }
        setItem(USER_KEY, JSON.stringify(this.userInfo))
      }
      return me
    },

    /** 退出登录（后端失败也要清理本地登录态） */
    async logout () {
      try {
        await logoutApi()
      } catch (e) {
        // 忽略：本地登录态必须清理
      } finally {
        this.clearAuth()
      }
    },

    clearAuth () {
      this.token = ''
      this.userInfo = null
      removeItem(TOKEN_KEY)
      removeItem(USER_KEY)
    },

    /** 本地更新（如信用分调整后同步顶栏展示） */
    patchUser (partial) {
      this.userInfo = { ...(this.userInfo || {}), ...partial }
      setItem(USER_KEY, JSON.stringify(this.userInfo))
    }
  }
})
