/** 拼场 Team（契约 §4） */
import request from '@/utils/request'

/** GET /api/v1/teams?courtId=&date= → [{id,creatorName,courtName,resvDate,startTime,endTime,maxMembers,currentMembers,status,joined,isCreator}] */
export function fetchTeams (params) {
  return request.get('/teams', { params })
}

/** GET /api/v1/teams/{id} → 详情含 members */
export function fetchTeam (id) {
  return request.get(`/teams/${id}`)
}
