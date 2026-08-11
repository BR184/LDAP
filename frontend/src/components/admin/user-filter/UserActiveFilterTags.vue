<script setup lang="ts">
import { computed } from 'vue'
import type { UserFilters } from './types'

const props = defineProps<{
  filters: UserFilters
}>()

const emit = defineEmits<{
  (e: 'clear-field', key: keyof UserFilters): void
  (e: 'clear-all'): void
}>()

interface TagItem {
  key: keyof UserFilters
  label: string
}

const tags = computed<TagItem[]>(() => {
  const items: TagItem[] = []
  const f = props.filters
  if (f.keyword.trim()) items.push({ key: 'keyword', label: `关键词: ${f.keyword.trim()}` })
  if (f.employmentStatus) items.push({ key: 'employmentStatus', label: f.employmentStatus === 'ACTIVE' ? '在职状态: 在职' : '在职状态: 离职' })
  if (f.accessAllowed) items.push({ key: 'accessAllowed', label: f.accessAllowed === 'true' ? '准入: 允许' : '准入: 关闭' })
  if (f.userId.trim()) items.push({ key: 'userId', label: `用户ID: ${f.userId.trim()}` })
  if (f.realName.trim()) items.push({ key: 'realName', label: `姓名: ${f.realName.trim()}` })
  if (f.employeeNo.trim()) items.push({ key: 'employeeNo', label: `工号: ${f.employeeNo.trim()}` })
  if (f.mobile.trim()) items.push({ key: 'mobile', label: `手机: ${f.mobile.trim()}` })
  if (f.email.trim()) items.push({ key: 'email', label: `邮箱: ${f.email.trim()}` })
  if (f.intranetEmail.trim()) items.push({ key: 'intranetEmail', label: `内网邮箱: ${f.intranetEmail.trim()}` })
  if (f.jobTitle.trim()) items.push({ key: 'jobTitle', label: `职务: ${f.jobTitle.trim()}` })
  if (f.accountStatus) items.push({ key: 'accountStatus', label: `账号状态: ${f.accountStatus}` })
  if (f.sourceType) items.push({ key: 'sourceType', label: `来源: ${f.sourceType}` })
  if (f.roleCodes.length) items.push({ key: 'roleCodes', label: `角色: ${f.roleCodes.join(',')}` })
  if (f.createdRange?.length) items.push({ key: 'createdRange', label: `创建时间: ${f.createdRange[0]} ~ ${f.createdRange[1]}` })
  if (f.departmentRules.length) items.push({ key: 'departmentRules', label: `部门筛选: ${f.departmentRules.length} 条规则` })
  return items
})
</script>

<template>
  <div v-if="tags.length" class="active-filter-tags">
    <span class="active-filter-tags__label">已选条件：</span>
    <el-tag v-for="tag in tags" :key="tag.key" closable size="small" :disable-transitions="true" @close="emit('clear-field', tag.key)">
      {{ tag.label }}
    </el-tag>
    <el-button link size="small" type="primary" @click="emit('clear-all')">清空全部</el-button>
  </div>
</template>

<style scoped lang="scss">
.active-filter-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px dashed var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-light);
  font-size: 13px;

  &__label {
    color: var(--el-text-color-secondary);
  }
}
</style>
