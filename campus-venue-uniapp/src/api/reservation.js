/**
 * 预约接口 —— docs/API.md §3
 */
import { http } from '@/utils/request.js'
import { API_PREFIX } from '@/config/index.js'

/**
 * POST /api/v1/reservations
 * body {courtId,resvDate,startSlot,endSlot,requestId,remark}
 * -> {id,resvNo,status,amount,startTime,endTime}（冲突 code=30001）
 */
export function createReservation(data) {
  return http.post(`${API_PREFIX}/reservations`, data, { hideError: true })
}

/** GET /api/v1/reservations/my?status=&pageNum=&pageSize= -> 分页 */
export function getMyReservations(params) {
  return http.get(`${API_PREFIX}/reservations/my`, params)
}

/** GET /api/v1/reservations/{id} -> 单条 + members:[{userId,nickname}] */
export function getReservationDetail(id) {
  return http.get(`${API_PREFIX}/reservations/${id}`)
}

/** POST /api/v1/reservations/{id}/pay-mock -> {status} */
export function payMock(id) {
  return http.post(`${API_PREFIX}/reservations/${id}/pay-mock`, {})
}

/** POST /api/v1/reservations/{id}/cancel body {reason} -> {status,penalty} */
export function cancelReservation(id, reason) {
  return http.post(`${API_PREFIX}/reservations/${id}/cancel`, { reason })
}
