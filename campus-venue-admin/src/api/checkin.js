/** 签到 Checkin（契约 §6） */
import request from '@/utils/request'

/** GET /api/v1/checkin/records?reservationId= → [{id,reservationId,userName,checkinTime,channel}] */
export function fetchCheckinRecords (reservationId) {
  return request.get('/checkin/records', { params: { reservationId } })
}

/**
 * POST /api/v1/checkin/scan { code } → 现场核销（仅管理员）
 * 返回 { reservationId, resvNo, userName, studentNo, courtName, startTime, endTime, checkinTime }
 */
export function scanCheckin (code) {
  return request.post('/checkin/scan', { code })
}
