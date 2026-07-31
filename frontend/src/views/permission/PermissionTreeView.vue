<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { fetchPermissionTree } from '@/api/modules/permission'
import type { PermissionTreeNode } from '@/types/permission'

const permissionTreeQuery = useQuery({
  queryKey: ['permissions', 'tree'],
  queryFn: fetchPermissionTree,
})

const permissionTree = computed<PermissionTreeNode[]>(() => permissionTreeQuery.data.value || [])

function permissionTypeText(permissionType: string) {
  return permissionType === 'MENU' ? '菜单显示' : '接口权限'
}
</script>

<template>
  <PageContainer title="权限树" description="展示系统当前生效的接口权限与菜单显示权限，角色授权以此为唯一权限来源。">
    <el-card class="idm-card" shadow="never">
      <template #header>
        <div class="permission-header">
          <strong>权限点</strong>
          <span class="idm-muted">当前共 {{ permissionTree.length }} 个根权限节点</span>
        </div>
      </template>

      <el-tree
        v-loading="permissionTreeQuery.isLoading.value || permissionTreeQuery.isFetching.value"
        :data="permissionTree"
        default-expand-all
        node-key="id"
      >
        <template #default="{ data }">
          <div class="permission-node">
            <div class="permission-node__title">
              <strong>{{ data.permissionName }}</strong>
              <el-tag size="small" :type="data.permissionType === 'MENU' ? 'warning' : 'info'">
                {{ permissionTypeText(data.permissionType) }}
              </el-tag>
            </div>
            <span>{{ data.permissionCode }} / {{ data.action }} {{ data.resourcePath }}</span>
          </div>
        </template>
      </el-tree>
    </el-card>
  </PageContainer>
</template>

<style scoped lang="scss">
.permission-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.permission-node {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 6px 0;
}

.permission-node__title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.permission-node span {
  color: var(--idm-text-secondary);
  font-size: 12px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
</style>
