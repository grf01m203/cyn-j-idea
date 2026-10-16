/**
 * 统一请求封装（契约 docs/API.md 第 0 节）
 * - 统一注入 Authorization: <token>
 * - 统一解析 {code,message,data,traceId}
 * - code !== 0 统一 ElMessage 提示并 reject
 * - code === 20001（或 HTTP 401）清空登录态并跳转 /#/login
 * 成功时返回 data（业务数据），失败时 reject(Error)
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/store/user'
import { getItem } from '@/utils/storage'

export const TOKEN_KEY = 'cv_admin_token'
export const USER_KEY = 'cv_admin_user'

export const CODE_SUCCESS = 0
export const CODE_UNAUTHORIZED = 20001
export const CODE_FORBIDDEN = 20003

const service = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' }
})

let redirecting = false

function toLogin (message) {
  const userStore = useUserStore()
  userStore.clearAuth()
  if (!redirecting) {
    redirecting = true
    if (message) ElMessage.error(message)
    const current = router.currentRoute.value
    router
      .replace({
        path: '/login',
        query: current.path && current.path !== '/login' ? { redirect: current.fullPath } : undefined
      })
      .finally(() => {
        setTimeout(() => {
          redirecting = false
        }, 300)
      })
  }
}

service.interceptors.request.use(
  (config) => {
    const token = getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = token
    }
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response) => {
    const body = response.data
    // 非标准响应（如文件流）原样返回
    if (body === null || body === undefined || typeof body !== 'object' || !('code' in body)) {
      return body
    }
    const { code, message, data } = body
    if (code === CODE_SUCCESS) {
      return data
    }
    if (code === CODE_UNAUTHORIZED) {
      toLogin(message || '登录已失效，请重新登录')
      return Promise.reject(new Error(message || '登录已失效'))
    }
    ElMessage.error(message || `请求失败（code=${code}）`)
    return Promise.reject(new Error(message || `请求失败（code=${code}）`))
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      toLogin('登录已失效，请重新登录')
      return Promise.reject(error)
    }
    const body = error?.response?.data
    if (body && typeof body === 'object' && body.code === CODE_UNAUTHORIZED) {
      toLogin(body.message || '登录已失效，请重新登录')
      return Promise.reject(error)
    }
    let msg = body?.message
    if (!msg) {
      if (error.code === 'ECONNABORTED') msg = '请求超时，请稍后重试'
      else if (status === 404) msg = '接口不存在（404），请确认后端版本'
      else if (status >= 500) msg = '服务端异常，请稍后重试'
      else if (!error.response) msg = '无法连接后端服务（http://localhost:8080）'
      else msg = `请求失败（HTTP ${status}）`
    }
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default service
