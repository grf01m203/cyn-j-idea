/** 系统参数（管理端） */
import request from '@/utils/request'

/** GET /api/v1/admin/configs/checkin-window → {key,windowMinutes,defaultMinutes,minMinutes,maxMinutes,desc} */
export function getCheckinWindow () {
  return request.get('/admin/configs/checkin-window')
}

/** PUT /api/v1/admin/configs/checkin-window {windowMinutes} → 更新后的配置 */
export function updateCheckinWindow (windowMinutes) {
  return request.put('/admin/configs/checkin-window', { windowMinutes })
}
