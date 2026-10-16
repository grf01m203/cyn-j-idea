/**
 * 统一请求封装
 * - 自动携带 Authorization: <token>
 * - 统一解析 {code,message,data}
 * - code != 0 -> reject 并 toast 后端 message
 * - 401 / code 20001 -> 清理登录态并跳转登录页
 */
import { API_PREFIX, REQUEST_BASE, STORAGE_KEYS } from '@/config/index.js'
import { toast } from '@/utils/date.js'

let redirecting = false

/** 读取本地 token */
export function getToken() {
  try {
    return uni.getStorageSync(STORAGE_KEYS.token) || ''
  } catch (e) {
    return ''
  }
}

/** 写入 / 清理 token */
export function setToken(token) {
  uni.setStorageSync(STORAGE_KEYS.token, token || '')
}
export function clearToken() {
  uni.removeStorageSync(STORAGE_KEYS.token)
}

/** 跳转登录（防止并发重复跳转） */
function toLogin() {
  if (redirecting) return
  redirecting = true
  clearToken()
  uni.removeStorageSync(STORAGE_KEYS.user)
  const pages = getCurrentPages ? getCurrentPages() : []
  const current = pages.length ? pages[pages.length - 1].route : ''
  if (current && current.indexOf('pages/login/index') !== -1) {
    redirecting = false
    return
  }
  setTimeout(() => {
    uni.reLaunch({
      url: '/pages/login/index',
      complete: () => {
        redirecting = false
      }
    })
  }, 300)
}

/** 拼接 query string */
export function buildQuery(params = {}) {
  return Object.keys(params)
    .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
    .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
}

/**
 * 基础请求
 * @param {object} options {url, method, data, query, header, hideError, hideLoading, auth}
 * @returns {Promise<any>} resolve data 字段
 */
export function request(options = {}) {
  const {
    url,
    method = 'GET',
    data = {},
    query = null,
    header = {},
    hideError = false,
    hideLoading = false,
    auth = true,
    timeout = 15000
  } = options

  const qs = query ? buildQuery(query) : ''
  const finalUrl = qs ? `${url}${url.indexOf('?') === -1 ? '?' : '&'}${qs}` : url

  const finalHeader = {
    'Content-Type': 'application/json',
    ...header
  }
  if (auth) {
    const token = getToken()
    if (token) finalHeader.Authorization = token
  }

  if (!hideLoading) {
    // 先 hide 再 show，避免前一次请求的 loading 残留
    uni.hideLoading()
    uni.showLoading({ title: '加载中', mask: true })
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: `${REQUEST_BASE}${finalUrl}`,
      method: method.toUpperCase(),
      data,
      header: finalHeader,
      timeout,
      success: (res) => {
        const { statusCode, data: body } = res

        // HTTP 层未登录
        if (statusCode === 401) {
          if (!hideError) toast('登录已失效，请重新登录')
          toLogin()
          reject({ code: 20001, message: '登录已失效，请重新登录' })
          return
        }
        if (statusCode < 200 || statusCode >= 300) {
          const msg = (body && body.message) || `请求失败(${statusCode})`
          if (!hideError) toast(msg)
          reject({ code: statusCode, message: msg })
          return
        }

        // 业务层
        if (body && typeof body === 'object' && 'code' in body) {
          if (body.code === 0) {
            resolve(body.data)
            return
          }
          if (body.code === 20001) {
            if (!hideError) toast(body.message || '登录已失效，请重新登录')
            toLogin()
            reject(body)
            return
          }
          if (!hideError) toast(body.message || '操作失败')
          reject(body)
          return
        }

        // 非标准响应体，直接返回
        resolve(body)
      },
      fail: (err) => {
        const msg = '网络异常，请检查后端服务是否已启动'
        if (!hideError) toast(msg)
        reject({ code: -1, message: msg, raw: err })
      },
      complete: () => {
        if (!hideLoading) uni.hideLoading()
      }    })
  })
}

export const http = {
  get: (url, params = {}, opts = {}) =>
    request({ url, method: 'GET', query: params, ...opts }),
  post: (url, data = {}, opts = {}) => {
    const { query, ...rest } = opts
    return request({ url, method: 'POST', data, query, ...rest })
  },
  put: (url, data = {}, opts = {}) => {
    const { query, ...rest } = opts
    return request({ url, method: 'PUT', data, query, ...rest })
  },
  del: (url, data = {}, opts = {}) => {
    const { query, ...rest } = opts
    return request({ url, method: 'DELETE', data, query, ...rest })
  }
}

export { API_PREFIX }
export default request
