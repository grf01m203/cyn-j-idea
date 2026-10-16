/**
 * 页面导航与登录校验辅助
 */
import { STORAGE_KEYS } from '@/config/index.js'

/** tabBar 页面集合（跳转这些页面必须用 switchTab） */
const TAB_PAGES = [
  '/pages/venue/list',
  '/pages/reservation/list',
  '/pages/team/list',
  '/pages/notice/list',
  '/pages/mine/index'
]

export function isLogin() {
  try {
    return !!uni.getStorageSync(STORAGE_KEYS.token)
  } catch (e) {
    return false
  }
}

/** 当前页面路径 */
export function currentRoute() {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  return pages.length ? pages[pages.length - 1].route : ''
}

/**
 * 需要登录的页面在 onShow 中调用
 * @returns {boolean} 是否已登录
 */
export function requireLogin(tip = '请先登录') {
  if (isLogin()) return true
  uni.showToast({ title: tip, icon: 'none' })
  setTimeout(() => {
    uni.reLaunch({ url: '/pages/login/index' })
  }, 500)
  return false
}

/** 跳转（自动区分 tabBar 页与普通页） */
export function navigate(url) {
  const path = url.split('?')[0]
  if (TAB_PAGES.indexOf(path) !== -1) {
    uni.switchTab({ url: path })
  } else {
    uni.navigateTo({ url })
  }
}

/** 登录成功后进入首页 */
export function goHome() {
  const url = '/pages/venue/list'
  uni.reLaunch({
    url,
    fail: () => {
      uni.switchTab({
        url,
        fail: () => {
          uni.navigateTo({ url })
        }
      })
    }
  })
}

/** 从页面参数中取值（onLoad options） */
export function paramOf(options, key, def = '') {
  if (!options) return def
  const v = options[key]
  return v === undefined || v === null || v === '' ? def : decodeURIComponent(v)
}
