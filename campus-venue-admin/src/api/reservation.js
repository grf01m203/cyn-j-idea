/** 预约 Reservation（契约 §3） */
import request from '@/utils/request'

/** GET /api/v1/admin/reservations?status=&date=&pageNum=&pageSize= → 分页 */
export function fetchAdminReservations (params) {
  return request.get('/admin/reservations', { params })
}

/** GET /api/v1/reservations/{id} → 单条 + members */
export function fetchReservation (id) {
  return request.get(`/reservations/${id}`)
}

/** POST /api/v1/reservations/{id}/cancel {reason} → {status,penalty}（管理员强制取消） */
export function cancelReservation (id, reason) {
  return request.post(`/reservations/${id}/cancel`, { reason })
}
