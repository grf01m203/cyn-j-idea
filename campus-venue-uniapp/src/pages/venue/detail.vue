<template>
  <view class="page">
    <view class="page-body">
      <!-- 场馆信息 -->
      <view class="card">
        <text class="venue-name">{{ venueName || '场馆详情' }}</text>
        <text class="text-sm text-muted">场馆 ID：{{ venueId }}</text>
      </view>

      <!-- 场地列表 -->
      <view class="card">
        <view class="row-between section-head">
          <text class="card-title">选择场地</text>
          <text class="text-sm text-muted" v-if="courts.length">{{ courts.length }} 个场地</text>
        </view>

        <StateView
          :status="courtState"
          loading-text="正在加载场地…"
          :error-text="courtError"
          empty-text="该场馆暂未开放场地"
          @retry="loadCourts"
        >
          <view
            v-for="c in courts"
            :key="c.id"
            class="court-item"
            :class="{ active: c.id === courtId, disabled: !courtUsable(c) }"
            @click="pickCourt(c)"
          >
            <view class="row-between court-top">
              <text class="court-name ellipsis">{{ c.name }}</text>
              <text class="text-primary text-bold court-price">¥{{ money(c.pricePerHour) }}/小时</text>
            </view>
            <view class="row-between court-meta">
              <text class="text-sm text-muted">类型：{{ c.type || '-' }} · 容纳 {{ c.capacity || '-' }} 人</text>
              <text class="tag" :class="courtUsable(c) ? 'tag-green' : 'tag-gray'">
                {{ courtUsable(c) ? '可预约' : '维护中' }}
              </text>
            </view>
          </view>
        </StateView>
      </view>

      <!-- 日期 + 时段 -->
      <view class="card" v-if="courtId">
        <view class="row-between section-head">
          <text class="card-title">选择时段</text>
          <text class="text-sm text-muted">{{ date }}</text>
        </view>

        <DatePicker v-model="date" @change="onDateChange" />

        <StateView
          :status="slotState"
          loading-text="正在加载时段…"
          :error-text="slotError"
          empty-text="当天无可预约时段"
          @retry="loadSlots"
        >
          <SlotGrid
            :slots="slots"
            v-model:startSlot="startSlot"
            v-model:endSlot="endSlot"
            :max-duration="maxDuration"
            @change="onSlotChange"
          />
        </StateView>
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar" v-if="courtId">
      <view class="bar-info">
        <text class="bar-price" v-if="startSlot !== null && endSlot !== null">
          ¥{{ money(estimatedAmount) }}
        </text>
        <text class="bar-price bar-price-empty" v-else>未选时段</text>
        <text class="text-sm text-muted" v-if="startSlot !== null && endSlot !== null">
          {{ slotToTime(startSlot) }}~{{ slotToTime(endSlot) }} · {{ (endSlot - startSlot) / 2 }} 小时
        </text>
        <text class="text-sm text-muted" v-else>请点选起止时段</text>
      </view>
      <button class="btn btn-default btn-sm bar-btn-mini" @click="openMore">候补/拼场</button>
      <button class="btn btn-primary btn-sm bar-btn" :disabled="!canSubmit" @click="openReserve">
        立即预约
      </button>
    </view>

    <!-- 预约确认弹窗 -->
    <Modal
      v-model="reserveVisible"
      title="确认预约"
      confirm-text="提交预约"
      :confirm-disabled="submitting"
      @confirm="submitReservation"
    >
      <view class="kv"><text class="kv-k">场地</text><text class="kv-v">{{ currentCourt && currentCourt.name }}</text></view>
      <view class="kv"><text class="kv-k">日期</text><text class="kv-v">{{ date }}</text></view>
      <view class="kv">
        <text class="kv-k">时段</text>
        <text class="kv-v">{{ slotToTime(startSlot) }} ~ {{ slotToTime(endSlot) }}</text>
      </view>
      <view class="kv"><text class="kv-k">时长</text><text class="kv-v">{{ (endSlot - startSlot) / 2 }} 小时</text></view>
      <view class="kv">
        <text class="kv-k">预估金额</text>
        <text class="kv-v text-primary text-bold">¥{{ money(estimatedAmount) }}</text>
      </view>
      <view class="kv-col">
        <text class="kv-k">备注</text>
        <input class="remark-input" v-model="remark" placeholder="选填，如：需要球拍" placeholder-class="ph" />
      </view>
      <text class="text-sm text-muted">
        提交后请在「我的预约」中完成模拟支付。同一请求有幂等标识，重复提交不会生成多单。
      </text>
    </Modal>

    <!-- 加入候补弹窗 -->
    <Modal v-model="queueVisible" title="加入候补" confirm-text="确认加入" :confirm-disabled="queueSubmitting" @confirm="submitQueue">
      <view class="kv"><text class="kv-k">场地</text><text class="kv-v">{{ currentCourt && currentCourt.name }}</text></view>
      <view class="kv"><text class="kv-k">日期</text><text class="kv-v">{{ date }}</text></view>
      <view class="kv">
        <text class="kv-k">时段</text>
        <text class="kv-v">{{ slotToTime(startSlot) }} ~ {{ slotToTime(endSlot) }}</text>
      </view>
      <text class="text-sm text-muted">
        该时段被释放后，系统按排队顺序通知你，10 分钟内确认即可获得预约单。
      </text>
    </Modal>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import StateView from '@/components/StateView.vue'
import DatePicker from '@/components/DatePicker.vue'
import SlotGrid from '@/components/SlotGrid.vue'
import Modal from '@/components/Modal.vue'
import { getVenueCourts, getCourtSlots } from '@/api/venue.js'
import { createReservation } from '@/api/reservation.js'
import { joinQueue } from '@/api/queue.js'
import { createTeam } from '@/api/team.js'
import { money, slotToTime, today, genRequestId, toast } from '@/utils/date.js'
import { requireLogin, paramOf } from '@/utils/nav.js'

const venueId = ref('')
const venueName = ref('')
const courts = ref([])
const courtId = ref('')
const courtState = ref('loading')
const courtError = ref('加载失败，请稍后重试')

const date = ref(today())
const slots = ref([])
const slotState = ref('loading')
const slotError = ref('加载失败，请稍后重试')
const startSlot = ref(null)
const endSlot = ref(null)
const maxDuration = ref(0)

const reserveVisible = ref(false)
const queueVisible = ref(false)
const submitting = ref(false)
const queueSubmitting = ref(false)
const remark = ref('')
/** 幂等请求号：同一次选座复用，避免重复下单 */
let requestId = ''

const currentCourt = computed(() => courts.value.find((c) => c.id === courtId.value) || null)

/**
 * 场地是否可预约
 * 注意：契约只说明 `status` 字段存在，未定义场地状态枚举（status:0/1/2 的语义是**时段** slots 的）。
 * 这里按最保守的方式判断：仅当 status 明确等于 2 时视为不可用，其余视为可用。
 */
function courtUsable(c) {
  if (!c) return false
  if (String(c.status) === 'DISABLED' || String(c.status) === 'CLOSED') return false
  return Number(c.status) !== 2
}

const estimatedAmount = computed(() => {
  if (startSlot.value === null || endSlot.value === null || !currentCourt.value) return 0
  const hours = (endSlot.value - startSlot.value) / 2
  return Number(currentCourt.value.pricePerHour || 0) * hours
})

const canSubmit = computed(() => startSlot.value !== null && endSlot.value !== null && !submitting.value)

async function loadCourts() {
  courtState.value = 'loading'
  try {
    const data = await getVenueCourts(venueId.value)
    courts.value = Array.isArray(data) ? data : []
    courtState.value = courts.value.length ? 'success' : 'empty'
    if (courts.value.length) {
      const firstUsable = courts.value.find((c) => courtUsable(c)) || courts.value[0]
      pickCourt(firstUsable)
    }
  } catch (e) {
    courtError.value = (e && e.message) || '加载失败，请稍后重试'
    courtState.value = 'error'
  }
}

async function loadSlots() {
  if (!courtId.value) return
  slotState.value = 'loading'
  startSlot.value = null
  endSlot.value = null
  requestId = ''
  try {
    const data = await getCourtSlots(courtId.value, date.value)
    slots.value = (data && data.slots) || []
    maxDuration.value = (data && data.maxDuration) || 0
    slotState.value = slots.value.length ? 'success' : 'empty'
  } catch (e) {
    slotError.value = (e && e.message) || '加载失败，请稍后重试'
    slotState.value = 'error'
  }
}

function pickCourt(c) {
  if (!courtUsable(c)) {
    toast('该场地维护中，暂不可预约')
    return
  }
  if (courtId.value === c.id) return
  courtId.value = c.id
  loadSlots()
}

function onDateChange() {
  loadSlots()
}

function onSlotChange() {
  // 选中的时段发生变化 -> 生成新的幂等号
  requestId = ''
}

function openReserve() {
  if (startSlot.value === null || endSlot.value === null) {
    toast('请先点选起止时段')
    return
  }
  if (!requestId) requestId = genRequestId()
  remark.value = ''
  reserveVisible.value = true
}

async function submitReservation() {
  if (submitting.value) return
  submitting.value = true
  const payload = {
    courtId: courtId.value,
    resvDate: date.value,
    startSlot: startSlot.value,
    endSlot: endSlot.value,
    requestId: requestId || (requestId = genRequestId()),
    remark: remark.value
  }
  try {
    const data = await createReservation(payload)
    reserveVisible.value = false
    // 复用同一 requestId 重试即为幂等，成功后作废
    requestId = ''
    uni.showModal({
      title: '预约成功',
      content: `单号：${(data && data.resvNo) || '-'}\n金额：¥${money(data && data.amount)}\n状态：待支付`,
      confirmText: '去支付',
      cancelText: '稍后',
      success: (res) => {
        if (res.confirm) {
          uni.navigateTo({ url: '/pages/reservation/list' })
        } else {
          loadSlots()
        }
      }
    })
  } catch (e) {
    // 冲突（30001）等业务错误：把后端 message 原样弹给用户
    if (e && e.code === 30001) {
      uni.showModal({
        title: '时段冲突',
        content: e.message || '该时段已被占用，请重新选择',
        showCancel: false,
        confirmText: '重新选择'
      })
      reserveVisible.value = false
      loadSlots()
    } else if (e && e.message) {
      toast(e.message)
    }
    // 幂等：保留 requestId 供用户重试
  } finally {
    submitting.value = false
  }
}

function openQueue() {
  if (startSlot.value === null || endSlot.value === null) {
    toast('请先点选候补的起止时段')
    return
  }
  queueVisible.value = true
}

/** 底部「候补/拼场」入口 */
function openMore() {
  if (startSlot.value === null || endSlot.value === null) {
    toast('请先点选时段，再选择候补或拼场')
    return
  }
  uni.showActionSheet({
    itemList: ['加入候补（时段已被占用时排队）', '发起拼场（邀请同学一起拼）'],
    success: (res) => {
      if (res.tapIndex === 0) openQueue()
      if (res.tapIndex === 1) startTeam()
    }
  })
}

async function submitQueue() {
  if (queueSubmitting.value) return
  queueSubmitting.value = true
  try {
    const data = await joinQueue({
      courtId: courtId.value,
      resvDate: date.value,
      startSlot: startSlot.value,
      endSlot: endSlot.value
    })
    queueVisible.value = false
    uni.showModal({
      title: '已加入候补',
      content: `当前排队位置：第 ${(data && data.position) || '-'} 位\n有可用时段时会推送消息通知你`,
      showCancel: false,
      success: () => {
        uni.navigateTo({ url: '/pages/queue/list' })
      }
    })
  } catch (e) {
    if (e && e.message) toast(e.message)
  } finally {
    queueSubmitting.value = false
  }
}

/** 发起拼场：先创建拼场单，再跳转拼场详情 */
async function startTeam() {
  if (startSlot.value === null || endSlot.value === null) {
    toast('请先点选拼场时段')
    return
  }
  try {
    const data = await createTeam({
      courtId: courtId.value,
      resvDate: date.value,
      startSlot: startSlot.value,
      endSlot: endSlot.value,
      maxMembers: 4,
      remark: '拼场'
    })
    uni.navigateTo({ url: `/pages/team/detail?id=${data.id}` })
  } catch (e) {
    if (e && e.message) toast(e.message)
  }
}

onLoad((options) => {
  if (!requireLogin()) return
  venueId.value = paramOf(options, 'id')
  venueName.value = paramOf(options, 'name')
  loadCourts()
})
</script>

<style scoped>
.venue-name {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
  color: #1f2329;
  margin-bottom: 10rpx;
}
.section-head {
  margin-bottom: 20rpx;
}
.court-item {
  border: 1rpx solid #eceef1;
  border-radius: 12rpx;
  padding: 20rpx;
  margin-bottom: 16rpx;
}
.court-item.active {
  border-color: #1677ff;
  background-color: #f7faff;
}
.court-item.disabled {
  opacity: 0.6;
}
.court-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2329;
  flex: 1;
  min-width: 0;
  margin-right: 16rpx;
}
.court-price {
  flex-shrink: 0;
}
.court-meta {
  margin-top: 12rpx;
}
.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: #ffffff;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: row;
  align-items: center;
  z-index: 80;
}
.bar-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.bar-price {
  font-size: 32rpx;
  color: #ff4d4f;
  font-weight: 700;
}
.bar-price-empty {
  font-size: 26rpx;
  color: #8a9099;
  font-weight: 400;
}
.bar-btn {
  flex-shrink: 0;
  width: 180rpx;
  margin-left: 12rpx;
}
.bar-btn-mini {
  flex-shrink: 0;
  width: 150rpx;
  margin-left: 12rpx;
  font-size: 22rpx;
  padding: 0 8rpx;
}
.kv {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 14rpx 0;
  border-bottom: 1rpx solid #f5f6f8;
}
.kv-k {
  font-size: 26rpx;
  color: #8a9099;
}
.kv-v {
  font-size: 26rpx;
  color: #1f2329;
}
.kv-col {
  padding: 14rpx 0;
}
.kv-col .kv-k {
  display: block;
  margin-bottom: 8rpx;
}
.remark-input {
  height: 72rpx;
  background-color: #f7f8fa;
  border-radius: 10rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
}
.ph {
  color: #b3b8bf;
}
</style>
