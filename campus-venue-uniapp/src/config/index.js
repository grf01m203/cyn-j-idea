/**
 * 全局配置常量
 * - H5：走 Vite 代理（vite.config.js 里 /api、/ws -> Spring Boot 后端 http://localhost:8080），因此 baseUrl 留空
 * - 微信小程序：没有 Vite 代理，直接使用 BASE_URL（真机调试请改为局域网 IP 或 HTTPS 域名）
 * 后端固定为 Spring Boot 服务 campus-venue-server，本项目不包含任何 mock 后端。
 */
// #ifdef H5
const H5_MODE = true
// #endif
// #ifndef H5
const H5_MODE = false
// #endif

/**
 * 后端（Spring Boot）服务地址，仅小程序 / App 端使用。
 * 可通过构建环境变量覆盖：VITE_API_BASE=http://192.168.1.100:8080 npm run build:mp-weixin
 */
export const BASE_URL =
  (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_API_BASE) ||
  'http://localhost:8080'

/** 请求前缀 */
export const API_PREFIX = '/api/v1'

/** 实际请求根地址：H5 走代理用相对路径，小程序端用绝对地址 */
export const REQUEST_BASE = H5_MODE ? '' : BASE_URL

/** WebSocket 地址 */
export const WS_URL = H5_MODE
  ? `ws://${typeof location !== 'undefined' ? location.host : 'localhost:5173'}/ws/notice`
  : `${BASE_URL.replace(/^http/, 'ws')}/ws/notice`

/** 分页默认大小 */
export const PAGE_SIZE = 10

/** 本地存储 key */
export const STORAGE_KEYS = {
  token: 'cv_token',
  user: 'cv_user',
  lastNoticeId: 'cv_last_notice_id'
}

/** 预约状态字典 */
export const RESV_STATUS = {
  0: { text: '待支付', color: '#fa8c16' },
  1: { text: '已确认', color: '#1677ff' },
  2: { text: '已签到', color: '#52c41a' },
  3: { text: '已完成', color: '#8c8c8c' },
  4: { text: '未签到', color: '#ff4d4f' },
  5: { text: '已取消', color: '#8c8c8c' },
  6: { text: '已过期', color: '#8c8c8c' },
  7: { text: '逾期取消', color: '#ff4d4f' }
}

/** 拼场状态字典 */
export const TEAM_STATUS = {
  0: { text: '招募中', color: '#1677ff' },
  1: { text: '人数已满', color: '#fa8c16' },
  2: { text: '已成团', color: '#52c41a' },
  3: { text: '已解散', color: '#8c8c8c' }
}

/** 候补状态字典 */
export const QUEUE_STATUS = {
  0: { text: '等待中', color: '#8c8c8c' },
  1: { text: '已通知', color: '#fa8c16' },
  2: { text: '已确认', color: '#52c41a' },
  3: { text: '已过期', color: '#8c8c8c' },
  4: { text: '已取消', color: '#8c8c8c' }
}

/** 拼场成员状态字典 */
export const MEMBER_STATUS = {
  0: { text: '待审核', color: '#fa8c16' },
  1: { text: '已通过', color: '#52c41a' },
  2: { text: '已拒绝', color: '#ff4d4f' },
  3: { text: '已退出', color: '#8c8c8c' }
}
