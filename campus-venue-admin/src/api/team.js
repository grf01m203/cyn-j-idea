/** 拼场 Team（管理端 /api/v1/admin/teams，学生端 /api/v1/teams） */
import request from '@/utils/request'

/** 学生端：GET /api/v1/teams?courtId=&date= → [{id,creatorName,courtName,...}] */
export function fetchTeams (params) {
  return request.get('/teams', { params })
}

/** 学生端：GET /api/v1/teams/{id} → 详情含 members */
export function fetchTeam (id) {
  return request.get(`/teams/${id}`)
}

/**
 * 管理端：GET /api/v1/admin/teams?status=&courtId=&date=&pageNum=&pageSize=
 * → PageResult{records,total,pageNum,pageSize}
 * record: id,courtId,courtName,creatorId,creatorName,resvDate,startSlot,endSlot,
 *         startTime,endTime,maxMembers,currentMembers,status,reservationId,remark,expireTime,createTime
 */
export function fetchAdminTeams (params) {
  return request.get('/admin/teams', { params })
}

/** 管理端：GET /api/v1/admin/teams/{id} → 详情，含 members:[{id,userId,nickname,status,isLeader,isMe}] */
export function fetchAdminTeam (id) {
  return request.get(`/admin/teams/${id}`)
}

/**
 * 管理端：POST /api/v1/admin/teams/{id}/dissolve —— 强制解散
 * 已成团（status=2）时后端返回 code=30009，message 形如「该拼场已成团并生成预约单…」
 */
export function dissolveAdminTeam (id) {
  return request.post(`/admin/teams/${id}/dissolve`)
}
