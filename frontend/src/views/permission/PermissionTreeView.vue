<script setup lang="ts">
const permissionTree = [
  {
    id: 'auth',
    label: '认证模块',
    children: [
      { id: 'AUTH_ME', label: '查看当前用户 /api/v1/auth/me' },
    ],
  },
  {
    id: 'user',
    label: '用户管理',
    children: [
      { id: 'USER_READ', label: '用户查询 /api/v1/users' },
      { id: 'USER_CREATE', label: '用户创建 /api/v1/users' },
    ],
  },
]
</script>

<template>
  <PageContainer title="权限树" description="权限树页用于展示后端 API 权限点结构，为角色授权页提供认知基础。">
    <el-card class="idm-card" shadow="never">
      <template #header>
        <div class="permission-header">
          <strong>后端权限点</strong>
          <span class="idm-muted">后续可接入 `/api/v1/permissions/tree` 的真实数据</span>
        </div>
      </template>

      <el-tree :data="permissionTree" node-key="id" default-expand-all />
    </el-card>
  </PageContainer>
</template>

<style scoped lang="scss">
.permission-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
