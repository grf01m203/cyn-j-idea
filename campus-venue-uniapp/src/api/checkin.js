/**
 * 签到接口 —— docs/API.md §6
 */
import { http } from '@/utils/request.js'
import { API_PREFIX } from '@/config/index.js'

/** POST /api/v1/checkin/code body {reservationId} -> {code,expireAt}（一次性，60 秒有效） */
export function getCheckinCode(reservationId) {
  return http.post(`${API_PREFIX}/checkin/code`, { reservationId })
}

/** POST /api/v1/checkin/scan body {code} -> {reservationId,status:2,checkinTime} */
export function scanCheckin(code) {
  return http.post(`${API_PREFIX}/checkin/scan`, { code })
}

/** GET /api/v1/checkin/records?reservationId= -> [{id,reservationId,userName,checkinTime,channel}] */
export function getCheckinRecords(reservationId) {
  return http.get(`${API_PREFIX}/checkin/records`, { reservationId })
}
