/**
 * 实时消息 WebSocket 封装
 * - 连接 ws://<host>/ws/notice?token=<token>（H5 走 Vite 代理 /ws，小程序端直连 BASE_URL）
 * - 收到 {msgId,type,title,content,...} 后：回调全局 toast + 未读计数 +1
 * - 按 msgId 去重
 * - 断线指数退避自动重连（1s -> 2s -> ... 封顶 30s）
 * - 重连成功后调用 GET /api/v1/notifications/poll?lastId=<本地最大id> 补齐增量（保证不丢）
 */
import { WS_URL, STORAGE_KEYS, API_PREFIX } from '@/config/index.js'
import { getToken, request } from '@/utils/request.js'

const MAX_BACKOFF = 30000
const BASE_BACKOFF = 1000
/** 本地已处理的最大消息 id，用于轮询补齐 */
const seenIds = new Set()
let socketTask = null
let connecting = false
let manualClosed = false
let retryCount = 0
let retryTimer = null
let heartbeatTimer = null
let fallbackPollTimer = null
let started = false

/** 业务回调注册处 */
const handlers = {
  /** (msg) => void 新消息（已去重） */
  onMessage: null,
  /** (count) => void 未读数量变化（增量 +n） */
  onUnreadDelta: null,
  /** (state) => void 连接状态：connecting / open / close / reconnecting */
  onStateChange: null,
  /** (list) => void 轮询补齐到的历史消息 */
  onPollMessages: null
}

export function onNotice(handler) {
  Object.assign(handlers, handler || {})
}

/** 读取本地最大消息 id */
export function getLastId() {
  try {
    return Number(uni.getStorageSync(STORAGE_KEYS.lastNoticeId)) || 0
  } catch (e) {
    return 0
  }
}

/** 记录最大消息 id */
function bumpLastId(id) {
  const n = Number(id)
  if (!n || isNaN(n)) return
  if (n > getLastId()) {
    try {
      uni.setStorageSync(STORAGE_KEYS.lastNoticeId, n)
    } catch (e) {
      /* ignore */
    }
  }
}

function emitState(state) {
  if (handlers.onStateChange) handlers.onStateChange(state)
}

/** 解析消息体 */
function parseMessage(raw) {
  if (!raw) return null
  if (typeof raw === 'object') return raw
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}

/** 统一处理一条消息（去重 + 计数 + 回调） */
function dispatch(msg) {
  if (!msg) return
  const id = msg.msgId != null ? msg.msgId : msg.id
  if (id != null) {
    if (seenIds.has(String(id))) return
    seenIds.add(String(id))
    bumpLastId(id)
  }
  if (handlers.onMessage) handlers.onMessage(msg)
  if (handlers.onUnreadDelta) handlers.onUnreadDelta(1)
}

/**
 * 轮询补齐：重连后用 lastId 拉取增量消息
 */
export function pollMissed() {
  const token = getToken()
  if (!token) return Promise.resolve([])
  return request({
    url: `${API_PREFIX}/notifications/poll`,
    method: 'GET',
    data: { lastId: getLastId() },
    hideError: true,
    hideLoading: true
  })
    .then((list) => {
      const arr = Array.isArray(list) ? list : []
      arr
        .slice()
        .sort((a, b) => Number(a.id) - Number(b.id))
        .forEach((item) => {
          const id = item.id != null ? item.id : item.msgId
          if (id != null) {
            if (seenIds.has(String(id))) return
            seenIds.add(String(id))
            bumpLastId(id)
          }
          if (handlers.onPollMessages) handlers.onPollMessages(item)
          if (handlers.onMessage) handlers.onMessage({ ...item, msgId: id, fromPoll: true })
        })
      return arr
    })
    .catch(() => [])
}

/** 清理定时器 */
function clearTimers() {
  if (retryTimer) {
    clearTimeout(retryTimer)
    retryTimer = null
  }
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

/** 指数退避重连 */
function scheduleReconnect() {
  if (manualClosed) return
  clearTimers()
  const delay = Math.min(BASE_BACKOFF * Math.pow(2, retryCount), MAX_BACKOFF)
  retryCount += 1
  emitState('reconnecting')
  retryTimer = setTimeout(() => {
    connect()
  }, delay)
}

/** 关闭连接 */
export function closeNoticeSocket() {
  manualClosed = true
  clearTimers()
  if (socketTask) {
    try {
      socketTask.close({ code: 1000, reason: 'client close' })
    } catch (e) {
      /* ignore */
    }
    socketTask = null
  }
  started = false
  if (fallbackPollTimer) {
    clearInterval(fallbackPollTimer)
    fallbackPollTimer = null
  }
  emitState('close')
}

/** 建立连接 */
export function connect() {
  const token = getToken()
  if (!token) return
  if (connecting || (socketTask && socketTask.readyState === 1)) return

  manualClosed = false
  connecting = true
  emitState('connecting')

  const url = `${WS_URL}?token=${encodeURIComponent(token)}`
  socketTask = uni.connectSocket({
    url,
    complete: () => {}
  })

  if (!socketTask) {
    connecting = false
    scheduleReconnect()
    return
  }

  socketTask.onOpen(() => {
    connecting = false
    retryCount = 0
    emitState('open')
    // 重连后补齐增量消息，保证不丢
    pollMissed()
    // 心跳，防止中间层空闲断链
    if (heartbeatTimer) clearInterval(heartbeatTimer)
    heartbeatTimer = setInterval(() => {
      try {
        socketTask && socketTask.send({ data: 'ping' })
      } catch (e) {
        /* ignore */
      }
    }, 25000)
  })

  socketTask.onMessage((res) => {
    const msg = parseMessage(res && res.data)
    if (!msg) return
    if (msg.msgId == null && msg.id == null) return // 心跳回包等忽略
    dispatch(msg)
  })

  socketTask.onError(() => {
    connecting = false
    scheduleReconnect()
  })

  socketTask.onClose(() => {
    connecting = false
    socketTask = null
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
    if (manualClosed) {
      emitState('close')
      return
    }
    scheduleReconnect()
  })
}

/** 启动（登录后调用；重复调用安全） */
export function startNoticeSocket() {
  if (started) {
    connect()
    return
  }
  started = true
  seenIds.clear()
  retryCount = 0
  manualClosed = false
  connect()
  // 兜底轮询：即使 WS 不可用（如后端未开 WS），也能拿到消息
  if (!fallbackPollTimer) {
    fallbackPollTimer = setInterval(() => {
      if (!getToken()) return
      if (!socketTask || socketTask.readyState !== 1) pollMissed()
    }, 30000)
  }
}
