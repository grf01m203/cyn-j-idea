<template>
  <scroll-view class="date-scroll" scroll-x :show-scrollbar="false">
    <view class="date-row">
      <view
        v-for="item in dates"
        :key="item.value"
        class="date-chip"
        :class="{ active: item.value === modelValue }"
        @click="onPick(item)"
      >
        <text class="date-week">{{ item.week }}</text>
        <text class="date-day">{{ item.label }}</text>
      </view>
    </view>
  </scroll-view>
</template>

<script setup>
import { computed } from 'vue'
import { formatDate, today } from '@/utils/date.js'

const props = defineProps({
  modelValue: { type: String, default: '' },
  /** 可选天数（从今天起） */
  days: { type: Number, default: 14 }
})
const emit = defineEmits(['update:modelValue', 'change'])

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const dates = computed(() => {
  const list = []
  const base = new Date(today().replace(/-/g, '/'))
  for (let i = 0; i < props.days; i++) {
    const d = new Date(base.getTime())
    d.setDate(base.getDate() + i)
    const value = formatDate(d)
    list.push({
      value,
      week: i === 0 ? '今天' : i === 1 ? '明天' : WEEK[d.getDay()],
      label: `${d.getMonth() + 1}/${d.getDate()}`
    })
  }
  return list
})

function onPick(item) {
  if (item.value === props.modelValue) return
  emit('update:modelValue', item.value)
  emit('change', item.value)
}
</script>

<style scoped>
.date-scroll {
  white-space: nowrap;
  width: 100%;
}
.date-row {
  display: flex;
  flex-direction: row;
  padding: 8rpx 0;
}
.date-chip {
  flex-shrink: 0;
  width: 120rpx;
  height: 108rpx;
  border-radius: 12rpx;
  background-color: #ffffff;
  margin-right: 16rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1rpx solid #eceef1;
}
.date-chip.active {
  background-color: #1677ff;
  border-color: #1677ff;
}
.date-week {
  font-size: 22rpx;
  color: #8a9099;
}
.date-day {
  font-size: 26rpx;
  color: #1f2329;
  margin-top: 6rpx;
}
.date-chip.active .date-week,
.date-chip.active .date-day {
  color: #ffffff;
}
</style>
