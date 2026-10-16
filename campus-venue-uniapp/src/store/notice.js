/**
 * 消息中心 store：未读计数 + 实时消息
 * WebSocket 收到消息 / 轮询补齐后，未读 +1，并由 App.vue 统一 toast
 */
import { defineStore } from 'pinia'
import { getUnreadCount } from '@/api/notice.js'

export const useNoticeStore = defineStore('notice', {
  state: () => ({
    unread: 0,
    /** 实时到达的最新消息（用于全局 toast） */
    lastPush: null,
    /** 本地已展示过的消息 id（去重，防止 WS + 轮询重复） */
    seen: [],
    wsState: 'close'
  }),

  getters: {
    hasUnread: (state) => state.unread > 0
  },

  actions: {
    /** 从后端同步未读数 */
    async refreshUnread() {
      try {
        const data = await getUnreadCount()
        this.unread = (data && data.count) || 0
      } catch (e) {
        /* 静默失败，不打断页面 */
      }
    },

    /** 未读 +n */
    incr(n = 1) {
      this.unread = Math.max(0, this.unread + n)
    },

    /** 设为 0（全部已读） */
    clearUnread() {
      this.unread = 0
    },

    setUnread(n) {
      this.unread = Math.max(0, Number(n) || 0)
    },

    /**
     * 接收一条实时/补齐消息
     * @returns {boolean} 是否为新消息（未被去重）
     */
    push(msg) {
      if (!msg) return false
      const id = msg.msgId != null ? msg.msgId : msg.id
      if (id != null && this.seen.indexOf(String(id)) !== -1) return false
      if (id != null) {
        this.seen.push(String(id))
        if (this.seen.length > 300) this.seen.splice(0, this.seen.length - 300)
      }
      this.lastPush = msg
      this.incr(1)
      return true
    },

    onWsState(state) {
      this.wsState = state
    },

    reset() {
      this.unread = 0
      this.lastPush = null
      this.seen = []
      this.wsState = 'close'
    }
  }
})
