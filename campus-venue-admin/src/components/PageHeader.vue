<template>
  <div class="page-header">
    <div class="page-header__main">
      <div class="page-header__title">
        <el-icon v-if="icon" :size="18"><component :is="icon" /></el-icon>
        <span>{{ title }}</span>
      </div>
      <div v-if="description" class="page-header__desc">{{ description }}</div>
    </div>
    <div class="page-header__extra">
      <slot name="extra" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  icon: { type: String, default: '' }
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin: 16px 16px 0;
  padding: 14px 16px;
  background-color: #fff;
  border-radius: 6px;
  gap: 12px;
  flex-wrap: wrap;
}

.page-header__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.page-header__desc {
  margin-top: 6px;
  font-size: 12px;
  color: var(--cv-text-muted);
  line-height: 1.6;
}

.page-header__extra {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
