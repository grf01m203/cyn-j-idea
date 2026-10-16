<template>
  <view class="page">
    <view class="page-body">
      <!-- 搜索栏 -->
      <view class="search-wrap">
        <view class="search-bar">
          <text class="search-icon">🔍</text>
          <input
            v-model="keyword"
            placeholder="搜索场馆名称 / 地址"
            placeholder-class="ph"
            confirm-type="search"
            @confirm="onSearch"
            @input="onKeywordInput"
          />
          <text v-if="keyword" class="clear-btn" @click="onClear">✕</text>
        </view>
        <button class="btn btn-primary btn-sm search-btn" @click="onSearch">搜索</button>
      </view>

      <StateView
        :status="state"
        loading-text="正在加载场馆…"
        :error-text="errorMsg"
        empty-text="没有找到符合条件的场馆"
        empty-action-text="重置搜索"
        :show-empty-action="!!keyword"
        @retry="load(true)"
        @action="onClear"
      >
        <view class="venue-card" v-for="item in list" :key="item.id" @click="goDetail(item)">
          <view class="row-between">
            <text class="venue-name ellipsis">{{ item.name }}</text>
            <text class="tag tag-blue">{{ item.courtCount || 0 }} 个场地</text>
          </view>
          <text class="venue-addr ellipsis">📍 {{ item.address || '地址待完善' }}</text>
          <view class="row-between venue-foot">
            <text class="text-sm text-muted">🕘 {{ item.openTime || '--' }} - {{ item.closeTime || '--' }}</text>
            <text class="text-sm text-muted" v-if="item.phone">☎ {{ item.phone }}</text>
          </view>
          <text class="venue-desc" v-if="item.description">{{ item.description }}</text>
        </view>

        <view class="load-more" v-if="list.length">
          <text v-if="loadingMore">加载中…</text>
          <text v-else-if="finished">— 已加载全部 {{ total }} 条 —</text>
          <text v-else @click="loadMore">点击加载更多</text>
        </view>
      </StateView>
    </view>
    <UnreadFab />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import StateView from '@/components/StateView.vue'
import UnreadFab from '@/components/UnreadFab.vue'
import { getVenues } from '@/api/venue.js'
import { PAGE_SIZE } from '@/config/index.js'
import { requireLogin, isLogin } from '@/utils/nav.js'
import { debounce } from '@/utils/date.js'
import { useNoticeStore } from '@/store/notice.js'

const noticeStore = useNoticeStore()

const keyword = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const state = ref('loading') // loading | error | empty | success
const errorMsg = ref('加载失败，请稍后重试')
const loadingMore = ref(false)
const finished = ref(false)
let firstShow = true

async function load(reset = false) {
  if (reset) {
    pageNum.value = 1
    finished.value = false
    state.value = 'loading'
  }
  try {
    const data = await getVenues({
      keyword: keyword.value.trim(),
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE
    })
    const records = (data && data.records) || []
    total.value = (data && data.total) || 0
    list.value = reset ? records : list.value.concat(records)
    finished.value = list.value.length >= total.value || records.length === 0
    state.value = list.value.length ? 'success' : 'empty'
  } catch (e) {
    errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
    if (reset || !list.value.length) state.value = 'error'
  } finally {
    loadingMore.value = false
  }
}

function loadMore() {
  if (loadingMore.value || finished.value) return
  loadingMore.value = true
  pageNum.value += 1
  load(false)
}

function onSearch() {
  load(true)
}

const onKeywordInput = debounce(() => {
  load(true)
}, 500)

function onClear() {
  keyword.value = ''
  load(true)
}

function goDetail(item) {
  uni.navigateTo({ url: `/pages/venue/detail?id=${item.id}&name=${encodeURIComponent(item.name || '')}` })
}

onShow(() => {
  if (!requireLogin()) return
  // 刷新未读红点
  noticeStore.refreshUnread()
  // 从 detail 返回时刷新第一页，保证价格/占用状态最新
  firstShow = false
  load(true)
})

onPullDownRefresh(async () => {
  if (!isLogin()) {
    uni.stopPullDownRefresh()
    return
  }
  await load(true)
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  loadMore()
})
</script>

<style scoped>
.search-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 20rpx;
}
.search-bar {
  flex: 1;
  display: flex;
  flex-direction: row;
  align-items: center;
  background-color: #ffffff;
  border-radius: 12rpx;
  padding: 0 20rpx;
  height: 80rpx;
}
.search-icon {
  font-size: 28rpx;
  margin-right: 12rpx;
}
.search-bar input {
  flex: 1;
  font-size: 28rpx;
  height: 80rpx;
}
.clear-btn {
  font-size: 26rpx;
  color: #8a9099;
  padding: 0 8rpx;
}
.search-btn {
  margin-left: 16rpx;
  width: 140rpx;
}
.ph {
  color: #b3b8bf;
}
.venue-card {
  background-color: #ffffff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}
.venue-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #1f2329;
  max-width: 420rpx;
}
.venue-addr {
  display: block;
  font-size: 26rpx;
  color: #4e5969;
  margin-top: 14rpx;
}
.venue-foot {
  margin-top: 14rpx;
}
.venue-desc {
  display: block;
  font-size: 24rpx;
  color: #8a9099;
  margin-top: 12rpx;
  line-height: 1.5;
}
</style>
