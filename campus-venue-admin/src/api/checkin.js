/** 签到 Checkin（契约 §6） */
import request from '@/utils/request'

/** GET /api/v1/checkin/records?reservationId= → [{id,reservationId,userName,checkinTime,channel}] */
export function fetchCheckinRecords (reservationId) {
  return request.get('/checkin/records', { params: { reservationId } })
}
