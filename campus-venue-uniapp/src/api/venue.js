/**
 * 场馆 / 场地 / 时段接口 —— docs/API.md §2
 */
import { http } from '@/utils/request.js'
import { API_PREFIX } from '@/config/index.js'

/**
 * GET /api/v1/venues?keyword=&pageNum=&pageSize=
 * -> 分页 {id,name,address,openTime,closeTime,phone,description,courtCount}
 */
export function getVenues(params) {
  return http.get(`${API_PREFIX}/venues`, params)
}

/** GET /api/v1/venues/{id}/courts -> [{id,venueId,name,type,capacity,pricePerHour,status}] */
export function getVenueCourts(venueId) {
  return http.get(`${API_PREFIX}/venues/${venueId}/courts`)
}

/**
 * GET /api/v1/courts/{id}/slots?date=yyyy-MM-dd
 * -> {courtId,courtName,date,slots:[{slotNo,label,status}]} status: 0空闲 1已占用 2不可用
 */
export function getCourtSlots(courtId, date) {
  return http.get(`${API_PREFIX}/courts/${courtId}/slots`, { date })
}
