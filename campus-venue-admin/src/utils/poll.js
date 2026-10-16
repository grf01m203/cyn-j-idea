/** 简单轮询控制器：用于消息中心 lastId 增量轮询 */
export function createPoller (fn, interval = 5000) {
  let timer = null
  let stopped = true

  async function tick () {
    if (stopped) return
    try {
      await fn()
    } catch (e) {
      // 轮询失败静默，避免刷屏；下一次继续
    } finally {
      if (!stopped) timer = window.setTimeout(tick, interval)
    }
  }

  return {
    start () {
      if (!stopped) return
      stopped = false
      tick()
    },
    stop () {
      stopped = true
      if (timer) {
        window.clearTimeout(timer)
        timer = null
      }
    },
    get running () {
      return !stopped
    }
  }
}
