/**
 * 拼场接口 —— docs/API.md §4
 */
import { http } from '@/utils/request.js'
import { API_PREFIX } from '@/config/index.js'

/** POST /api/v1/teams body {courtId,resvDate,startSlot,endSlot,maxMembers,remark} -> {id} */
export function createTeam(data) {
  return http.post(`${API_PREFIX}/teams`, data)
}

/** GET /api/v1/teams?courtId=&date= -> [{id,creatorName,courtName,resvDate,startTime,endTime,maxMembers,currentMembers,status,joined,isCreator}] */
export function getTeams(params) {
  return http.get(`${API_PREFIX}/teams`, params)
}

/** GET /api/v1/teams/{id} -> {id,...,members:[{id,userId,nickname,status,isLeader,isMe}]} */
export function getTeamDetail(id) {
  return http.get(`${API_PREFIX}/teams/${id}`)
}

/** POST /api/v1/teams/{id}/join -> {memberId,status:0} */
export function joinTeam(id) {
  return http.post(`${API_PREFIX}/teams/${id}/join`, {})
}

/** POST /api/v1/teams/{id}/members/{memberId}/review?accept=true -> null */
export function reviewTeamMember(id, memberId, accept) {
  return http.post(`${API_PREFIX}/teams/${id}/members/${memberId}/review`, {}, {
    query: { accept: accept === true || accept === 'true' ? 'true' : 'false' }
  })
}

/** POST /api/v1/teams/{id}/quit -> null */
export function quitTeam(id) {
  return http.post(`${API_PREFIX}/teams/${id}/quit`, {})
}

/**
 * POST /api/v1/teams/{id}/confirm（队长确认成团）
 * -> {reservationId,resvNo,status,needPay,amount,memberCount,shareAmount}
 * 免费场地 status=2、needPay=false；收费场地 status=4、needPay=true
 */
export function confirmTeam(id) {
  return http.post(`${API_PREFIX}/teams/${id}/confirm`, {})
}

/**
 * POST /api/v1/teams/{id}/pay（当前登录成员支付自己的份额，模拟支付）
 * -> {teamId,paid,paidCount,memberCount,allPaid,amount,shareAmount,status,reservationId,message}
 * 全部付齐 status=2/allPaid=true；被抢先 status=5 且 message 有值（非错误响应）
 */
export function payTeamShare(id) {
  return http.post(`${API_PREFIX}/teams/${id}/pay`, {})
}

/** POST /api/v1/teams/{id}/dissolve -> null */
export function dissolveTeam(id) {
  return http.post(`${API_PREFIX}/teams/${id}/dissolve`, {})
}
