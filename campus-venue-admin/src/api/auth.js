/** 认证 Auth（契约 §1） */
import request from '@/utils/request'

/** POST /api/v1/auth/login → {token, user:{id,username,nickname,role,creditScore}} */
export function login (data) {
  return request.post('/auth/login', data)
}

/** GET /api/v1/auth/me */
export function getMe () {
  return request.get('/auth/me')
}

/** POST /api/v1/auth/logout */
export function logout () {
  return request.post('/auth/logout')
}
