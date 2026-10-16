<template>
  <el-table
    :data="data"
    v-loading="loading"
    :element-loading-text="loadingText"
    border
    stripe
    :height="height"
    :max-height="maxHeight"
    @selection-change="onSelectionChange"
  >
    <el-table-column v-if="selectable" type="selection" width="46" :selectable="selectableFn" />
    <slot />
    <template #empty>
      <el-empty :description="emptyText" :image-size="90" />
    </template>
  </el-table>

  <div v-if="showPagination" class="pagination-wrap">
    <el-pagination
      background
      layout="total, sizes, prev, pager, next, jumper"
      :total="total"
      :current-page="pageNum"
      :page-size="pageSize"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />
  </div>
</template>

<script setup>
const props = defineProps({
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  loadingText: { type: String, default: '加载中…' },
  emptyText: { type: String, default: '暂无数据' },
  showPagination: { type: Boolean, default: true },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  height: { type: [String, Number], default: undefined },
  maxHeight: { type: [String, Number], default: undefined },
  selectable: { type: Boolean, default: false },
  selectableFn: { type: Function, default: undefined }
})

const emit = defineEmits(['update:pageNum', 'update:pageSize', 'pagination-change', 'selection-change'])

function onPageChange (page) {
  emit('update:pageNum', page)
  emit('pagination-change', { pageNum: page, pageSize: props.pageSize })
}

function onSizeChange (size) {
  emit('update:pageSize', size)
  emit('update:pageNum', 1)
  emit('pagination-change', { pageNum: 1, pageSize: size })
}

function onSelectionChange (rows) {
  emit('selection-change', rows)
}
</script>
