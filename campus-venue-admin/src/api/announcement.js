/** 公告 Announcement（管理端 /api/v1/admin/announcements） */
import request from '@/utils/request'

/**
 * POST /api/v1/admin/announcements {title,content}
 * → {id,title,content,publishTime,receiverCount}
 */
export function createAnnouncement (data) {
  return request.post('/admin/announcements', data)
}

/**
 * GET /api/v1/admin/announcements?pageNum=&pageSize=
 * → PageResult{records:[{id,title,content,publishTime,createTime}],total,pageNum,pageSize}
 */
export function fetchAnnouncements (params) {
  return request.get('/admin/announcements', { params })
}

/** DELETE /api/v1/admin/announcements/{id} —— 撤销公告 */
export function deleteAnnouncement (id) {
  return request.delete(`/admin/announcements/${id}`)
}
