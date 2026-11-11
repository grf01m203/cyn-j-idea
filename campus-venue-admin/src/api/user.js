/** 用户与信用（契约 §9） */
import request from '@/utils/request'

/** GET /api/v1/admin/users?keyword=&pageNum=&pageSize= → 分页 */
export function fetchUsers (params) {
  return request.get('/admin/users', { params })
}

/** PUT /api/v1/admin/users/{id}/status {status}（1正常 0禁用） */
export function updateUserStatus (id, status) {
  return request.put(`/admin/users/${id}/status`, { status })
}

/** PUT /api/v1/admin/users/{id}/credit {score,reason}（score 为目标分值） */
export function updateUserCredit (id, data) {
  return request.put(`/admin/users/${id}/credit`, data)
}

/** PUT /api/v1/admin/users/{id}/blacklist/clear 解除黑名单（同时清零违约次数） */
export function clearUserBlacklist (id) {
  return request.put(`/admin/users/${id}/blacklist/clear`)
}

/** GET /api/v1/admin/users/export?keyword= → CSV 文件流 */
export function exportUsers (keyword) {
  return request.get('/admin/users/export', {
    params: { keyword },
    responseType: 'blob'
  })
}

/** GET /api/v1/credit/logs?pageNum=&pageSize= → 分页 */
export function fetchCreditLogs (params) {
  return request.get('/credit/logs', { params })
}

/** DELETE /api/v1/admin/users/{id} —— 逻辑删除用户（v0.6.0 新增） */
export function deleteUser (id) {
  return request.delete(`/admin/users/${id}`)
}
