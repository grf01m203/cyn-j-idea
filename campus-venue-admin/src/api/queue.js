/**
 * 候补 Queue（契约 §5）
 * 注意：契约中仅存在 /api/v1/queues（入队）、/api/v1/queues/my（我的候补）、
 * /api/v1/queues/{id}/confirm、/api/v1/queues/{id}/cancel，
 * 没有管理员侧"全部候补列表"接口，因此管理端不做候补全量列表，详见 README「已知限制」。
 */
import request from '@/utils/request'

/** GET /api/v1/queues/my —— 仅返回当前登录人的候补记录（管理端只能看到自己作为用户的记录） */
export function fetchMyQueues () {
  return request.get('/queues/my')
}
