<script setup lang="ts">
import { computed } from 'vue'
import { OfficeBuilding } from '@element-plus/icons-vue'
import type { UserFilters } from './types'

const props = defineProps<{
  filters: UserFilters
  roleOptions: { value: string; label: string }[]
  deptRuleCount: number
}>()

const emit = defineEmits<{
  (e: 'update:filters', value: UserFilters): void
  (e: 'open-dept-rules'): void
}>()

const filters = computed(() => props.filters)

const accountStatusOptions = ['正常', '锁定', '禁用']
const sourceTypeOptions = [
  { value: 'MANUAL', label: '手工创建' },
  { value: 'FEISHU', label: '飞书导入' },
]
</script>

<template>
  <el-form label-width="84px" class="user-advanced-filter">
    <el-row :gutter="16">
      <el-col :span="8">
        <el-form-item label="用户ID">
          <el-input v-model="filters.userId" clearable placeholder="精确匹配" />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="姓名">
          <el-input v-model="filters.realName" clearable placeholder="模糊搜索" />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="工号">
          <el-input v-model="filters.employeeNo" clearable placeholder="模糊搜索" />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="手机号">
          <el-input v-model="filters.mobile" clearable placeholder="模糊搜索" />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="工作邮箱">
          <el-input v-model="filters.email" clearable placeholder="模糊搜索" />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="内网邮箱">
          <el-input v-model="filters.intranetEmail" clearable placeholder="模糊搜索" />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="职务">
          <el-input v-model="filters.jobTitle" clearable placeholder="模糊搜索" />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="账号状态">
          <el-select v-model="filters.accountStatus" clearable placeholder="全部" style="width: 100%">
            <el-option v-for="status in accountStatusOptions" :key="status" :value="status" :label="status" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="来源">
          <el-select v-model="filters.sourceType" clearable placeholder="全部" style="width: 100%">
            <el-option v-for="option in sourceTypeOptions" :key="option.value" :value="option.value" :label="option.label" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="角色">
          <el-select v-model="filters.roleCodes" multiple clearable filterable collapse-tags placeholder="全部角色" style="width: 100%">
            <el-option v-for="option in roleOptions" :key="option.value" :value="option.value" :label="option.label" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="filters.createdRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="部门">
          <el-button style="width: 100%" @click="emit('open-dept-rules')" :icon="OfficeBuilding">
            部门筛选<span v-if="deptRuleCount" class="advanced-filter__badge">{{ deptRuleCount }}</span>
          </el-button>
        </el-form-item>
      </el-col>
    </el-row>
  </el-form>
</template>

<style scoped lang="scss">
.user-advanced-filter {
  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}

.advanced-filter__badge {
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
