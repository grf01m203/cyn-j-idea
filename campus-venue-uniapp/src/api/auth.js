/**
 * 认证模块接口 —— docs/API.md §1
 */
import { http } from '@/utils/request.js'
import { API_PREFIX } from '@/config/index.js'

/** POST /api/v1/auth/login  -> {token, user:{id,username,nickname,role,creditScore}} */
export function login(data) {
  return http.post(`${API_PREFIX}/auth/login`, data, { auth: false, hideError: true })
}

/** GET /api/v1/auth/me -> {id,username,nickname,studentNo,phone,role,creditScore,violateCount,blacklistUntil,status} */
export function getMe() {
  return http.get(`${API_PREFIX}/auth/me`)
}

/** POST /api/v1/auth/logout -> null */
export function logout() {
  return http.post(`${API_PREFIX}/auth/logout`, {}, { hideError: true })
}

/** PUT /api/v1/auth/avatar {url} -> 更新后的用户信息（头像先经 /files/upload 上传） */
export function updateAvatar(url) {
  return http.put(`${API_PREFIX}/auth/avatar`, { url })
}
