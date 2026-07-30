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
        <el-descriptions-item label="数据库ID">{{ user.id }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ user.userId }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ user.realName }}</el-descriptions-item>
        <el-descriptions-item label="工作邮箱">{{ user.email || '--' }}</el-descriptions-item>
        <el-descriptions-item label="内网邮箱">{{ user.intranetEmail || '--' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ user.mobile || '--' }}</el-descriptions-item>
        <el-descriptions-item label="工号">{{ user.employeeNo || '--' }}</el-descriptions-item>
        <el-descriptions-item label="职务">{{ user.jobTitle || '--' }}</el-descriptions-item>
        <el-descriptions-item label="直属上级">{{ user.directLeaderRaw || '--' }}</el-descriptions-item>
        <el-descriptions-item label="上级ID">{{ user.leaderRef || '--' }}</el-descriptions-item>
        <el-descriptions-item label="主部门">{{ user.departmentPath || user.deptName || '--' }}</el-descriptions-item>
        <el-descriptions-item label="兼职部门">
          <el-space v-if="user.partTimeDepartments.length" wrap>
            <el-tag
              v-for="department in user.partTimeDepartments"
              :key="department.deptCode"
              type="info"
            >
              {{ department.departmentPath || department.deptName || '--' }}
            </el-tag>
          </el-space>
          <span v-else>--</span>
        </el-descriptions-item>
        <el-descriptions-item label="允许使用">
          <el-tag :type="user.accessAllowed ? 'success' : 'danger'">
            {{ user.accessAllowed ? '允许' : '关闭' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="在职状态">{{ user.employmentStatus === 'RESIGNED' ? '离职' : '在职' }}</el-descriptions-item>
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
