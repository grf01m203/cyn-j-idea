/**
 * 通用工具函数
 */

/** 数字补零 */
function pad(n) {
  return n < 10 ? `0${n}` : `${n}`
}

/**
 * 格式化日期 yyyy-MM-dd
 * @param {Date|string|number} d
 */
export function formatDate(d = new Date()) {
  const date = d instanceof Date ? d : new Date(d)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/**
 * 格式化时间 yyyy-MM-dd HH:mm:ss
 * 兼容后端返回的 "2026-10-20T15:00:00" / "2026-10-20 15:00:00" / 时间戳
 */
export function formatDateTime(d = new Date()) {
  if (!d) return '-'
  let date
  if (typeof d === 'string') {
    date = new Date(d.replace(/-/g, '/').replace('T', ' ').replace(/\.\d+$/, ''))
  } else {
    date = d instanceof Date ? d : new Date(d)
  }
  if (isNaN(date.getTime())) return String(d)
  return `${formatDate(date)} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/** 只取 HH:mm 展示 */
export function shortTime(d) {
  if (!d) return '-'
  const s = String(d)
  const m = s.match(/(\d{2}:\d{2})(:\d{2})?/)
  if (m) return m[1]
  const dt = new Date(s)
  if (isNaN(dt.getTime())) return s
  return `${pad(dt.getHours())}:${pad(dt.getMinutes())}`
}

/** 今天 yyyy-MM-dd */
export function today() {
  return formatDate(new Date())
}

/**
 * 日期偏移
 * @param {number} offset 天数，可为负
 */
export function dateOffset(offset) {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return formatDate(d)
}

/** 剩余毫秒数 -> mm:ss */
export function countdownText(ms) {
  if (ms == null || ms < 0) ms = 0
  const total = Math.floor(ms / 1000)
  const m = Math.floor(total / 60)
  const s = total % 60
  return `${pad(m)}:${pad(s)}`
}

/**
 * 剩余毫秒数 -> 人类可读倒计时（天/时/分/秒，去掉前导零单位）
 * 例：1天 02:03:04 / 2小时 13:05 / 13:05
 */
export function durationText(ms) {
  if (ms == null || ms < 0) ms = 0
  const total = Math.floor(ms / 1000)
  const d = Math.floor(total / 86400)
  const h = Math.floor((total % 86400) / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const hms = `${pad(m)}:${pad(s)}`
  if (d > 0) return `${d}天 ${pad(h)}:${hms}`
  if (h > 0) return `${h}小时 ${hms}`
  return hms
}

/**
 * 解析后端时间字符串为毫秒时间戳（兼容 iOS 与多种分隔符）
 */
export function toTimestamp(d) {
  if (!d) return 0
  if (typeof d === 'number') return d
  const date = new Date(String(d).replace(/-/g, '/').replace('T', ' ').replace(/\.\d+$/, ''))
  const t = date.getTime()
  return isNaN(t) ? 0 : t
}

/**
 * slotNo -> HH:mm
 * 契约：slotNo/2 = 小时，slotNo=30 -> 15:00
 */
export function slotToTime(slotNo) {
  const h = Math.floor(slotNo / 2)
  const m = slotNo % 2 === 0 ? '00' : '30'
  return `${pad(h)}:${m}`
}

/** slotNo 区间 -> "15:00~16:00" */
export function slotRangeText(startSlot, endSlot) {
  return `${slotToTime(startSlot)}~${slotToTime(endSlot)}`
}

/**
 * 幂等 requestId：Date.now() + 随机串
 */
export function genRequestId() {
  return `${Date.now()}${Math.random().toString(36).slice(2, 10)}`
}

/** 金额展示 */
export function money(v) {
  if (v == null || v === '') return '0.00'
  const n = Number(v)
  return isNaN(n) ? String(v) : n.toFixed(2)
}

/** 轻提示 */
export function toast(title, icon = 'none', duration = 2000) {
  uni.showToast({ title: String(title || '操作失败'), icon, duration })
}

/** 统一错误提示（后端 message 直接展示） */
export function toastError(e) {
  const msg = typeof e === 'string' ? e : (e && (e.message || e.errMsg)) || '请求失败'
  toast(msg, 'none', 2200)
}

/** loading 包装 */
export function showLoading(title = '加载中') {
  uni.showLoading({ title, mask: true })
}
export function hideLoading() {
  uni.hideLoading()
}

/** 简易防抖 */
export function debounce(fn, delay = 400) {
  let timer = null
  return function (...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}
