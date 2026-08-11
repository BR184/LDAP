<script setup lang="ts">
import { computed } from 'vue'
import { Search, Refresh, ArrowDown, ArrowUp, OfficeBuilding } from '@element-plus/icons-vue'
import type { UserFilters } from './types'

const props = defineProps<{
  filters: UserFilters
  deptRuleCount: number
  advancedVisible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:filters', value: UserFilters): void
  (e: 'search'): void
  (e: 'reset'): void
  (e: 'toggle-advanced'): void
  (e: 'open-dept-rules'): void
}>()

const filters = computed(() => props.filters)
</script>

<template>
  <el-form :inline="true" class="user-quick-filter" @submit.prevent="emit('search')">
    <el-form-item label="综合关键词">
      <el-input v-model="filters.keyword" clearable placeholder="用户ID、工号或姓名" style="width: 220px" />
    </el-form-item>
    <el-form-item label="部门">
      <el-button @click="emit('open-dept-rules')" :icon="OfficeBuilding">
        部门筛选<span v-if="deptRuleCount" class="quick-filter__badge">{{ deptRuleCount }}</span>
      </el-button>
    </el-form-item>
    <el-form-item label="在职状态">
      <el-select v-model="filters.employmentStatus" clearable placeholder="全部" style="width: 120px">
        <el-option label="在职" value="ACTIVE" />
        <el-option label="离职" value="RESIGNED" />
      </el-select>
    </el-form-item>
    <el-form-item label="允许使用">
      <el-select v-model="filters.accessAllowed" clearable placeholder="全部" style="width: 120px">
        <el-option label="允许使用" value="true" />
        <el-option label="已关闭" value="false" />
      </el-select>
    </el-form-item>
    <el-form-item>
      <el-button type="primary" native-type="submit" :icon="Search">查询</el-button>
      <el-button :icon="Refresh" @click="emit('reset')">重置</el-button>
      <el-button text @click="emit('toggle-advanced')">
        {{ advancedVisible ? '收起精确筛选' : '精确筛选' }}
        <el-icon><ArrowUp v-if="advancedVisible" /><ArrowDown v-else /></el-icon>
      </el-button>
    </el-form-item>
  </el-form>
</template>

<style scoped lang="scss">
.user-quick-filter {
  :deep(.el-form-item) {
    margin-right: 16px;
    /* 保留底部间距，避免筛选项换行时与操作按钮行重叠 */
    margin-bottom: 10px;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 10px;
  }
}

.quick-filter__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  margin-left: 6px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 12px;
  line-height: 16px;
}
</style>
