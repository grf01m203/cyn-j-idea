/** 场馆 / 场地 / 开放规则（契约 §2） */
import request from '@/utils/request'

/** GET /api/v1/venues?keyword=&pageNum=&pageSize= → 分页 */
export function fetchVenues (params) {
  return request.get('/venues', { params })
}

/** GET /api/v1/venues/{id}/courts → [{id,venueId,name,type,capacity,pricePerHour,status}] */
export function fetchVenueCourts (venueId) {
  return request.get(`/venues/${venueId}/courts`)
}

/** GET /api/v1/courts/{id}/slots?date= */
export function fetchCourtSlots (courtId, date) {
  return request.get(`/courts/${courtId}/slots`, { params: { date } })
}

/** POST /api/v1/admin/venues → {id} */
export function createVenue (data) {
  return request.post('/admin/venues', data)
}

/** PUT /api/v1/admin/venues/{id} */
export function updateVenue (id, data) {
  return request.put(`/admin/venues/${id}`, data)
}

/** DELETE /api/v1/admin/venues/{id} */
export function deleteVenue (id) {
  return request.delete(`/admin/venues/${id}`)
}

/** POST /api/v1/admin/courts → {id} */
export function createCourt (data) {
  return request.post('/admin/courts', data)
}

/** PUT /api/v1/admin/courts/{id} */
export function updateCourt (id, data) {
  return request.put(`/admin/courts/${id}`, data)
}

/** DELETE /api/v1/admin/courts/{id} */
export function deleteCourt (id) {
  return request.delete(`/admin/courts/${id}`)
}

/** GET /api/v1/admin/open-rules?courtId= → [{id,courtId,weekday,startTime,endTime,slotMinutes,maxDuration,price}] */
export function fetchOpenRules (courtId) {
  return request.get('/admin/open-rules', { params: { courtId } })
}

/** PUT /api/v1/admin/courts/{id}/rules {rules:[...]} 整体覆盖 */
export function saveOpenRules (courtId, rules) {
  return request.put(`/admin/courts/${courtId}/rules`, { rules })
}
