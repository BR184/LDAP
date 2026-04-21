<script setup lang="ts">
import { reactive } from 'vue'
import { notifyPlanned } from '@/utils/placeholder'

const query = reactive({
  roleCode: '',
  status: '',
})

const rows = [
  { id: 1, roleCode: 'ADMIN', roleName: '管理员', permissionLevel: 1, status: 1 },
  { id: 2, roleCode: 'NORMAL_USER', roleName: '普通用户', permissionLevel: 5, status: 1 },
]
</script>

<template>
  <PageContainer title="角色管理" description="角色页用于维护角色基础信息，并承接菜单绑定、权限授权和用户角色分配。">
    <el-card class="idm-card" shadow="never">
      <el-form :inline="true" :model="query">
        <el-form-item label="角色编码">
          <el-input v-model="query.roleCode" placeholder="请输入角色编码" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 160px">
            <el-option label="启用" value="1" />
            <el-option label="禁用" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="notifyPlanned('角色查询')">查询</el-button>
          <el-button @click="query.roleCode = ''; query.status = ''">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="idm-card" shadow="never">
      <template #header>
        <div class="view-toolbar">
          <strong>角色列表</strong>
          <el-button type="primary" @click="notifyPlanned('新增角色')">新增角色</el-button>
        </div>
      </template>

      <el-table :data="rows">
        <el-table-column prop="roleCode" label="角色编码" min-width="160" />
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="permissionLevel" label="权限等级" min-width="120" />
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="260" fixed="right">
          <template #default="{ row }">
            <el-space wrap>
              <el-button link type="primary" @click="notifyPlanned(`编辑角色 ${row.roleCode}`)">编辑</el-button>
              <el-button link type="warning" @click="notifyPlanned(`绑定菜单 ${row.roleCode}`)">绑定菜单</el-button>
              <el-button link type="success" @click="notifyPlanned(`授权权限 ${row.roleCode}`)">授权权限</el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </PageContainer>
</template>

<style scoped lang="scss">
.view-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
</style>
