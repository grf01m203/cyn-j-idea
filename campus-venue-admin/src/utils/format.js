/**
 * 通用格式化 / 业务字典工具
 * 槽位规则（契约第 0 节）：slotNo/2 = 小时，slotNo=30 → 15:00
 */

/** 槽位号 → "HH:mm" */
export function slotNoToLabel (slotNo) {
  const n = Number(slotNo)
  if (!Number.isFinite(n) || n < 0) return '-'
  const h = Math.floor(n / 2)
  const m = (n % 2) * 30
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

/** "HH:mm(:ss)" 或 "yyyy-MM-dd HH:mm:ss" → "HH:mm" */
export function toHm (value, fallback = '-') {
  if (value === null || value === undefined || value === '') return fallback
  const s = String(value)
  const m = s.match(/(\d{1,2}):(\d{2})/)
  if (!m) return s
  return `${m[1].padStart(2, '0')}:${m[2]}`
}

/** 截取日期部分 "yyyy-MM-dd" */
export function toDate (value, fallback = '-') {
  if (!value) return fallback
  const s = String(value)
  const m = s.match(/\d{4}-\d{2}-\d{2}/)
  return m ? m[0] : s
}

/** yyyy-MM-dd */
export function formatDate (date) {
  const d = date instanceof Date ? date : new Date(date)
  if (Number.isNaN(d.getTime())) return ''
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 今天 yyyy-MM-dd */
export function today () {
  return formatDate(new Date())
}

/** 金额 */
export function toMoney (value, fallback = '0.00') {
  if (value === null || value === undefined || value === '') return fallback
  const n = Number(value)
  if (!Number.isFinite(n)) return fallback
  return n.toFixed(2)
}

/** 比率（0~1 或已是百分数）→ "12.3%" */
export function toPercent (value, digits = 1) {
  if (value === null || value === undefined || value === '') return '-'
  const n = Number(value)
  if (!Number.isFinite(n)) return '-'
  const rate = n > 1 ? n : n * 100
  return `${rate.toFixed(digits)}%`
}

/** 周一为一周之始：1~7 */
export function weekdayOf (dateStr) {
  const d = dateStr ? new Date(`${toDate(dateStr)}T00:00:00`) : new Date()
  if (Number.isNaN(d.getTime())) return 1
  const w = d.getDay()
  return w === 0 ? 7 : w
}

export const WEEKDAY_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

export function weekdayLabel (weekday) {
  const i = Number(weekday) - 1
  return WEEKDAY_LABELS[i] || `周${weekday}`
}

/** 预约状态（契约第 0 节） */
export const RESERVATION_STATUS = {
  0: { label: '待支付', type: 'warning' },
  1: { label: '已确认', type: 'primary' },
  2: { label: '已签到', type: 'success' },
  3: { label: '已完成', type: 'success' },
  4: { label: '未签到', type: 'danger' },
  5: { label: '已取消', type: 'info' },
  6: { label: '已过期', type: 'info' },
  7: { label: '逾期取消', type: 'danger' }
}

export function reservationStatusLabel (status) {
  return RESERVATION_STATUS[status]?.label ?? `未知(${status})`
}

export function reservationStatusType (status) {
  return RESERVATION_STATUS[status]?.type ?? 'info'
}

/** 拼场状态 */
export const TEAM_STATUS = {
  0: { label: '招募中', type: 'warning' },
  1: { label: '人数已满', type: 'primary' },
  2: { label: '已成团', type: 'success' },
  3: { label: '已解散', type: 'info' }
}

export function teamStatusLabel (status) {
  return TEAM_STATUS[status]?.label ?? `未知(${status})`
}

export function teamStatusType (status) {
  return TEAM_STATUS[status]?.type ?? 'info'
}

/** 候补入队状态 */
export const QUEUE_STATUS = {
  0: { label: '等待中', type: 'warning' },
  1: { label: '已通知', type: 'primary' },
  2: { label: '已确认', type: 'success' },
  3: { label: '已过期', type: 'info' },
  4: { label: '已取消', type: 'info' }
}

export function queueStatusLabel (status) {
  return QUEUE_STATUS[status]?.label ?? `未知(${status})`
}

export function queueStatusType (status) {
  return QUEUE_STATUS[status]?.type ?? 'info'
}

/** 用户状态 */
export function userStatusLabel (status) {
  return Number(status) === 1 ? '正常' : '禁用'
}

export function userStatusType (status) {
  return Number(status) === 1 ? 'success' : 'danger'
}

/** 角色 */
export const ROLE_LABELS = {
  STUDENT: '学生',
  VENUE_ADMIN: '场馆管理员',
  SYS_ADMIN: '系统管理员'
}

export function roleLabel (role) {
  return ROLE_LABELS[role] || role || '-'
}

/** 消息类型 */
export const NOTICE_TYPE_LABELS = {
  RESV_CREATED: '预约创建',
  RESV_CANCELLED: '预约取消',
  RESV_REMIND: '预约提醒',
  TEAM_INVITE: '拼场邀请',
  TEAM_FULL: '拼场满员',
  TEAM_DISSOLVED: '拼场解散',
  QUEUE_PROMOTED: '候补递补',
  CHECKIN_REMIND: '签到提醒',
  CREDIT_CHANGED: '信用分变动',
  ADMIN_NOTICE: '系统公告'
}

export function noticeTypeLabel (type) {
  return NOTICE_TYPE_LABELS[type] || type || '-'
}

/** 场地类型候选（后端为自由文本，仅作下拉建议） */
export const COURT_TYPES = ['羽毛球', '篮球', '网球', '乒乓球', '排球', '足球', '健身房', '自习室', '通用']

/** 校验 "HH:mm" 或 "HH:mm:ss" */
export function isTimeString (value) {
  return /^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(String(value || ''))
}

/** 转成后端 TIME 需要的 "HH:mm:ss" */
export function toTimeValue (value) {
  if (!value) return ''
  const s = String(value)
  return s.length === 5 ? `${s}:00` : s
}
