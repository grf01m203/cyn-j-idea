<template>
  <view class="slot-wrap">
    <view class="legend">
      <view class="legend-item"><view class="legend-dot free"></view><text class="text-sm">空闲</text></view>
      <view class="legend-item"><view class="legend-dot busy"></view><text class="text-sm">已占用</text></view>
      <view class="legend-item"><view class="legend-dot off"></view><text class="text-sm">不可用</text></view>
      <view class="legend-item"><view class="legend-dot picked"></view><text class="text-sm">已选</text></view>
    </view>

    <view class="grid">
      <view
        v-for="slot in slots"
        :key="slot.slotNo"
        class="slot"
        :class="slotClass(slot)"
        @click="onTap(slot)"
      >
        <text class="slot-label">{{ slot.label || labelOf(slot.slotNo) }}</text>
      </view>
    </view>

    <view class="picked-bar" v-if="startSlot !== null && endSlot !== null">
      <text class="text-sm">
        已选时段：<text class="text-primary text-bold">{{ slotToTime(startSlot) }}~{{ slotToTime(endSlot) }}</text>
        （共 {{ endSlot - startSlot }} 个 30 分钟）
      </text>
      <button class="btn btn-default btn-sm" @click="reset">清空</button>
    </view>
  </view>
</template>

<script setup>
/**
 * 30 分钟粒度时段网格
 * 三态：0 空闲 / 1 已占用 / 2 不可用
 * 点选逻辑：第一次点选起点，第二次点选终点；区间内必须全部空闲，否则防呆提示
 */
import { slotToTime } from '@/utils/date.js'

const props = defineProps({
  /** [{slotNo,label,status}] */
  slots: { type: Array, default: () => [] },
  startSlot: { type: Number, default: null },
  endSlot: { type: Number, default: null },
  /** 最大可选时长（小时），来自开馆规则；不传则不限 */
  maxDuration: { type: Number, default: 0 }
})
const emit = defineEmits(['update:startSlot', 'update:endSlot', 'change'])

function labelOf(slotNo) {
  return slotToTime(slotNo)
}

function slotClass(slot) {
  const s = Number(slot.status)
  const picked =
    props.startSlot !== null &&
    props.endSlot !== null &&
    slot.slotNo >= props.startSlot &&
    slot.slotNo < props.endSlot
  return {
    free: s === 0,
    busy: s === 1,
    off: s === 2,
    picked,
    'pick-start': slot.slotNo === props.startSlot,
    'pick-end': props.endSlot !== null && slot.slotNo === props.endSlot - 1
  }
}

function isFree(slotNo) {
  const s = props.slots.find((x) => Number(x.slotNo) === Number(slotNo))
  return s ? Number(s.status) === 0 : false
}

function reset() {
  emit('update:startSlot', null)
  emit('update:endSlot', null)
  emit('change', { startSlot: null, endSlot: null })
}

function onTap(slot) {
  const s = Number(slot.status)
  if (s === 1) {
    uni.showToast({ title: '该时段已被占用，不可选择', icon: 'none' })
    return
  }
  if (s === 2) {
    uni.showToast({ title: '该时段不可预约', icon: 'none' })
    return
  }
  const no = Number(slot.slotNo)

  // 未选起点，或已有完整区间 -> 重新开始
  if (props.startSlot === null || (props.startSlot !== null && props.endSlot !== null)) {
    emit('update:startSlot', no)
    emit('update:endSlot', null)
    emit('change', { startSlot: no, endSlot: null })
    return
  }

  // 已有起点，选终点
  let start = props.startSlot
  let end = no
  if (end < start) {
    // 反向点击：把当前点作为新起点
    emit('update:startSlot', no)
    emit('update:endSlot', null)
    emit('change', { startSlot: no, endSlot: null })
    return
  }
  if (end === start) {
    // 点自己：取消选择
    reset()
    return
  }
  end = end + 1 // 终点槽位也包含在内

  // 防呆 1：区间内不得包含已占用 / 不可用
  for (let i = start; i < end; i++) {
    const target = props.slots.find((x) => Number(x.slotNo) === i)
    if (!target || Number(target.status) !== 0) {
      uni.showToast({ title: `${slotToTime(i)} 时段不可选，请重新选择`, icon: 'none' })
      return
    }
  }
  // 防呆 2：最大时长限制
  if (props.maxDuration > 0 && (end - start) / 2 > props.maxDuration) {
    uni.showToast({ title: `单次最多预约 ${props.maxDuration} 小时`, icon: 'none' })
    return
  }

  emit('update:endSlot', end)
  emit('change', { startSlot: start, endSlot: end })
}
</script>

<style scoped>
.slot-wrap {
  width: 100%;
}
.legend {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  margin-bottom: 16rpx;
}
.legend-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-right: 24rpx;
}
.legend-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 4rpx;
  margin-right: 8rpx;
}
.legend-dot.free {
  background-color: #ffffff;
  border: 1rpx solid #c9cdd4;
}
.legend-dot.busy {
  background-color: #ffe1e0;
}
.legend-dot.off {
  background-color: #e5e6eb;
}
.legend-dot.picked {
  background-color: #1677ff;
}
.grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
}
.slot {
  width: 116rpx;
  height: 68rpx;
  margin: 0 8rpx 12rpx 0;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid #c9cdd4;
  background-color: #ffffff;
}
.slot-label {
  font-size: 22rpx;
  color: #1f2329;
}
.slot.busy {
  background-color: #ffe1e0;
  border-color: #ffc4c2;
}
.slot.busy .slot-label {
  color: #ff4d4f;
}
.slot.off {
  background-color: #f2f3f5;
  border-color: #e5e6eb;
}
.slot.off .slot-label {
  color: #b3b8bf;
}
.slot.picked {
  background-color: #1677ff;
  border-color: #1677ff;
}
.slot.picked .slot-label {
  color: #ffffff;
}
.picked-bar {
  margin-top: 12rpx;
  padding: 16rpx;
  background-color: #f7f9fc;
  border-radius: 10rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
</style>
