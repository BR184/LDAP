<script setup lang="ts">
import type { UserItem } from '@/types/user'

defineProps<{
  modelValue: boolean
  loading?: boolean
  user?: UserItem | null
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
}>()
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="用户详情"
    size="520px"
    @close="emit('update:modelValue', false)"
  >
    <el-skeleton :loading="loading" animated :rows="6">
      <template #template>
        <el-skeleton-item variant="p" style="width: 100%; height: 24px" />
      </template>

      <el-descriptions v-if="user" :column="1" border>
        <el-descriptions-item label="用户名">{{ user.username }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ user.realName }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ user.email || '--' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ user.mobile || '--' }}</el-descriptions-item>
        <el-descriptions-item label="工号">{{ user.employeeNo || '--' }}</el-descriptions-item>
        <el-descriptions-item label="部门编码">{{ user.deptCode || '--' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="user.status === 1 ? 'success' : 'danger'">
            {{ user.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-space wrap>
            <el-tag v-for="role in user.roleCodes" :key="role" type="info">{{ role }}</el-tag>
          </el-space>
        </el-descriptions-item>
        <el-descriptions-item label="LDAP DN">{{ user.ldapDn || '--' }}</el-descriptions-item>
      </el-descriptions>
    </el-skeleton>
  </el-drawer>
</template>
