import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'

/**
 * 【仅开发自测】H5 端支持 ?devlogin=<username> 自动登录，便于本地快速进入各页面做冒烟验证。
 * 逻辑：首次带 devlogin 参数时用演示账号登录，记住目标路由（hash）存 sessionStorage；
 *       登录完成后 reLaunch 到该路由。生产构建（import.meta.env.PROD）会整段跳过。
 */
function maybeDevLogin() {
  // #ifdef H5
  if (import.meta.env && import.meta.env.PROD) return
  try {
    const KEY = '__cv_devlogin_route__'
    const pending = window.sessionStorage.getItem(KEY)
    if (pending) {
      window.sessionStorage.removeItem(KEY)
      setTimeout(() => {
        uni.reLaunch({ url: pending, fail: () => uni.switchTab({ url: pending }) })
      }, 300)
      return
    }
    const sp = new URLSearchParams(window.location.search)
    const username = sp.get('devlogin')
    if (!username) return
    sp.delete('devlogin')
    const rest = sp.toString()
    const route = window.location.hash ? window.location.hash.replace(/^#/, '') : '/pages/venue/list'
    window.history.replaceState(
      null,
      '',
      window.location.pathname + (rest ? '?' + rest : '')
    )
    window.sessionStorage.setItem(KEY, route)
    Promise.all([import('@/store/auth.js'), import('@/store/notice.js')]).then(([m]) => {
      const auth = m.useAuthStore()
      auth
        .login(username, '123456')
        .then(() => {
          const target = window.sessionStorage.getItem(KEY) || '/pages/venue/list'
          window.sessionStorage.removeItem(KEY)
          uni.reLaunch({ url: target, fail: () => uni.switchTab({ url: target }) })
        })
        .catch((e) => {
          window.sessionStorage.removeItem(KEY)
          console.warn('[devlogin] failed', e)
        })
    })
  } catch (e) {
    /* ignore */
  }
  // #endif
}

export function createApp() {
  const app = createSSRApp(App)
  app.use(createPinia())
  maybeDevLogin()
  return { app }
}
