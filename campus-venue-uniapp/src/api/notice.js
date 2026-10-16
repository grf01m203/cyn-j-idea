/**
 * 消息通知接口 —— docs/API.md §7
 */
import { http } from '@/utils/request.js'
import { API_PREFIX } from '@/config/index.js'

/**
 * GET /api/v1/notifications?pageNum=&pageSize=&onlyUnread=
 * -> 分页 {id,type,title,content,bizType,bizId,readFlag,createTime}
 */
export function getNotifications(params) {
  return http.get(`${API_PREFIX}/notifications`, params)
}

/** GET /api/v1/notifications/poll?lastId=0 -> [{...}]（按 id 升序） */
export function pollNotifications(lastId) {
  return http.get(`${API_PREFIX}/notifications/poll`, { lastId }, { hideLoading: true, hideError: true })
}

/** POST /api/v1/notifications/{id}/read -> null */
export function readNotification(id) {
  return http.post(`${API_PREFIX}/notifications/${id}/read`, {})
}

/** POST /api/v1/notifications/read-all -> null */
export function readAllNotifications() {
  return http.post(`${API_PREFIX}/notifications/read-all`, {})
}

/** GET /api/v1/notifications/unread-count -> {count} */
export function getUnreadCount() {
  return http.get(`${API_PREFIX}/notifications/unread-count`, {}, { hideLoading: true, hideError: true })
}
