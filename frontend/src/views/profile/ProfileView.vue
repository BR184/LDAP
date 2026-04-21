<script setup lang="ts">
import { notifyPlanned } from '@/utils/placeholder'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
</script>

<template>
  <PageContainer title="个人中心" description="展示当前登录用户信息，并为修改密码等个人操作提供入口。">
    <el-card class="idm-card" shadow="never">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="账号">{{ authStore.currentUser?.username || '--' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ authStore.currentUser?.realName || '--' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ authStore.currentUser?.email || '--' }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ authStore.currentUser?.deptCode || '--' }}</el-descriptions-item>
      </el-descriptions>

      <div class="profile-actions">
        <el-button type="primary" @click="notifyPlanned('修改密码')">修改密码</el-button>
      </div>
    </el-card>
  </PageContainer>
</template>

<style scoped lang="scss">
.profile-actions {
  margin-top: 16px;
}
</style>
