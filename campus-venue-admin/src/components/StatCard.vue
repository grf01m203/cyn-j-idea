<template>
  <el-row :gutter="16" class="stat-card-row">
    <el-col v-for="item in items" :key="item.key" :xs="12" :sm="12" :md="8" :lg="6" :xl="6">
      <el-card shadow="hover" class="stat-card" v-loading="loading">
        <div class="stat-card__body">
          <div class="stat-card__icon" :style="{ backgroundColor: item.color + '1a', color: item.color }">
            <el-icon :size="22"><component :is="item.icon" /></el-icon>
          </div>
          <div class="stat-card__info">
            <div class="stat-card__label">{{ item.label }}</div>
            <div class="stat-card__value">{{ item.value }}</div>
          </div>
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup>
defineProps({
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})
</script>

<style scoped>
.stat-card-row {
  margin-bottom: 0;
}

.stat-card {
  margin-bottom: 16px;
  border: none;
}

.stat-card__body {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-card__icon {
  width: 46px;
  height: 46px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card__label {
  font-size: 13px;
  color: var(--cv-text-muted);
}

.stat-card__value {
  font-size: 22px;
  font-weight: 600;
  margin-top: 4px;
  line-height: 1.2;
}
</style>
