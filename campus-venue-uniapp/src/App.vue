<script>
import { useAuthStore } from '@/store/auth.js'
import { useNoticeStore } from '@/store/notice.js'
import { onNotice, startNoticeSocket, connect } from '@/utils/ws.js'

export default {
  onLaunch() {
    const auth = useAuthStore()
    // 首次启动统一绑定实时消息回调
    onNotice({
      onMessage(msg) {
        const notice = useNoticeStore()
        const isNew = notice.push(msg)
        if (isNew) {
          // 全局 toast 提示
          uni.showToast({
            title: msg.title || '收到一条新消息',
            icon: 'none',
            duration: 2600
          })
        }
      },
      onUnreadDelta() {
        // 未读计数已在 notice.push 中 +1
      },
      onStateChange(state) {
        useNoticeStore().onWsState(state)
      }
    })

    if (auth.isLogin) {
      startNoticeSocket()
      useNoticeStore().refreshUnread()
      // 静默刷新个人资料
      auth.fetchProfile().catch(() => {})
    }
  },

  onShow() {
    const auth = useAuthStore()
    if (auth.isLogin) {
      startNoticeSocket()
      connect()
      useNoticeStore().refreshUnread()
    }
  },

  onHide() {
    // 进入后台不主动断开，保持指数退避重连能力
  },

  onError(err) {
    console.error('[CampusVenue] app error', err)
  }
}
</script>

<style>
@import './static/index.css';
</style>
