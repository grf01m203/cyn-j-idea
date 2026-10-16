/**
 * 候补队列接口 —— docs/API.md §5
 */
import { http } from '@/utils/request.js'
import { API_PREFIX } from '@/config/index.js'

/** POST /api/v1/queues body {courtId,resvDate,startSlot,endSlot} -> {id,position} */
export function joinQueue(data) {
  return http.post(`${API_PREFIX}/queues`, data)
}

/** GET /api/v1/queues/my -> [{id,courtName,resvDate,startTime,endTime,status,expireTime,position}] */
export function getMyQueues() {
  return http.get(`${API_PREFIX}/queues/my`)
}

/** POST /api/v1/queues/{id}/confirm -> {reservationId}（10 分钟内确认） */
export function confirmQueue(id) {
  return http.post(`${API_PREFIX}/queues/${id}/confirm`, {})
}

/** POST /api/v1/queues/{id}/cancel -> null */
export function cancelQueue(id) {
  return http.post(`${API_PREFIX}/queues/${id}/cancel`, {})
}
