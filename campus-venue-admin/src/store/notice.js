import { defineStore } from 'pinia'
import {
  fetchNotifications,
  pollNotifications,
  readNotification,
  readAllNotifications,
  fetchUnreadCount
} from '@/api/notice'
import { createPoller } from '@/utils/poll'

/** 消息中心：顶栏未读红点 + 页面列表，5 秒轮询 GET /notifications/poll?lastId= 兜底 */
export const useNoticeStore = defineStore('notice', {
  state: () => ({
    unreadCount: 0,
    items: [],
    total: 0,
    loading: false,
    // poll 用：当前已见过的最大 id
    lastId: 0,
    // poll 得到的增量消息（用于顶部弹窗/红点提示）
    latest: [],
    poller: null
  }),
  getters: {
    hasUnread: (state) => state.unreadCount > 0
  },
  actions: {
    async loadUnreadCount () {
      try {
        const data = await fetchUnreadCount()
        this.unreadCount = Number(data?.count ?? 0)
      } catch (e) {
        // 静默：顶栏红点失败不打断页面
      }
      return this.unreadCount
    },

    async loadList ({ pageNum = 1, pageSize = 10, onlyUnread = false } = {}) {
      this.loading = true
      try {
        const page = await fetchNotifications({ pageNum, pageSize, onlyUnread })
        const records = page?.records || []
        this.items = records
        this.total = Number(page?.total ?? records.length)
        // 记录最大 id，避免轮询把历史消息重复推成"新消息"
        records.forEach((item) => {
          if (Number(item.id) > this.lastId) this.lastId = Number(item.id)
        })
        return page
      } finally {
        this.loading = false
      }
    },

    /** 增量拉取；返回本次新增的消息 */
    async poll () {
      const list = await pollNotifications(this.lastId)
      const records = Array.isArray(list) ? list : []
      if (!records.length) return []
      records.forEach((item) => {
        if (Number(item.id) > this.lastId) this.lastId = Number(item.id)
      })
      const fresh = records.filter((item) => !item.read)
      fresh.forEach((item) => {
        const exist = this.items.find((it) => it.id === item.id)
        if (!exist) this.items.unshift(item)
      })
      if (fresh.length) {
        this.unreadCount += fresh.length
        this.latest = fresh
      }
      return fresh
    },

    async markRead (id) {
      await readNotification(id)
      const target = this.items.find((it) => it.id === id)
      if (target && !target.read) {
        target.read = true
        target.readFlag = 1
      }
      await this.loadUnreadCount()
    },

    async markAllRead () {
      await readAllNotifications()
      this.items.forEach((it) => {
        it.read = true
        it.readFlag = 1
      })
      this.unreadCount = 0
    },

    startPolling (interval = 5000) {
      if (!this.poller) {
        this.poller = createPoller(() => this.poll(), interval)
      }
      this.poller.start()
    },

    stopPolling () {
      if (this.poller) this.poller.stop()
    },

    reset () {
      this.stopPolling()
      this.poller = null
      this.unreadCount = 0
      this.items = []
      this.total = 0
      this.lastId = 0
      this.latest = []
    }
  }
})
