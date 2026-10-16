/** 消息 Notice（契约 §7） */
import request from '@/utils/request'

/** GET /api/v1/notifications?pageNum=&pageSize=&onlyUnread= → 分页 */
export function fetchNotifications (params) {
  return request.get('/notifications', { params })
}

/** GET /api/v1/notifications/poll?lastId=0 → [{...}] 按 id 升序 */
export function pollNotifications (lastId) {
  return request.get('/notifications/poll', { params: { lastId } })
}

/** POST /api/v1/notifications/{id}/read */
export function readNotification (id) {
  return request.post(`/notifications/${id}/read`)
}

/** POST /api/v1/notifications/read-all */
export function readAllNotifications () {
  return request.post('/notifications/read-all')
}

/** GET /api/v1/notifications/unread-count → {count} */
export function fetchUnreadCount () {
  return request.get('/notifications/unread-count')
}
