/** 统计 Stat（契约 §8） */
import request from '@/utils/request'

/** GET /api/v1/admin/stat/overview → {userCount,venueCount,courtCount,resvToday,resvTotal,cancelRate,signRate} */
export function fetchOverview () {
  return request.get('/admin/stat/overview')
}

/** GET /api/v1/admin/stat/trend?days=7 → [{date,resvCount,cancelCount,signCount}] */
export function fetchTrend (days = 7) {
  return request.get('/admin/stat/trend', { params: { days } })
}

/** GET /api/v1/admin/stat/heatmap?startDate=&endDate= → [{weekday,slotNo,label,rate}] */
export function fetchHeatmap (params) {
  return request.get('/admin/stat/heatmap', { params })
}
