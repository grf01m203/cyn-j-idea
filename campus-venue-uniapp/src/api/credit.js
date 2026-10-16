/**
 * 信用分接口 —— docs/API.md §9
 */
import { http } from '@/utils/request.js'
import { API_PREFIX } from '@/config/index.js'

/**
 * GET /api/v1/credit/logs?pageNum=&pageSize=
 * -> 分页 {id,changeValue,afterScore,reason,bizType,bizId,createTime}
 */
export function getCreditLogs(params) {
  return http.get(`${API_PREFIX}/credit/logs`, params)
}
