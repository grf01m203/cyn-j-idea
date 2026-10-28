/**
 * 候补 Queue
 * 管理端：GET /api/v1/admin/queues?status=&courtId=&date=&pageNum=&pageSize=
 * 学生端：GET /api/v1/queues/my（仅当前登录人自己的候补记录）
 */
import request from '@/utils/request'

/**
 * 管理端：候补队列全局分页
 * → PageResult{records,total,pageNum,pageSize}
 * record: id,courtId,courtName,userName,resvDate,startSlot,endSlot,startTime,endTime,
 *         status,expireTime,reservationId,createTime,position（仅 status=0 有 position）
 */
export function fetchAdminQueues (params) {
  return request.get('/admin/queues', { params })
}

/** 学生端：GET /api/v1/queues/my —— 仅返回当前登录人的候补记录 */
export function fetchMyQueues () {
  return request.get('/queues/my')
}
