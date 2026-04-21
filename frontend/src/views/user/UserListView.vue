<script setup lang="ts">
import { reactive } from 'vue'
import { notifyPlanned } from '@/utils/placeholder'

const query = reactive({
  username: '',
  deptCode: '',
  status: '',
})

const rows = [
  {
    id: 1,
    username: 'admin',
    realName: '系统管理员',
    deptCode: 'D001',
    status: 1,
    roles: ['ADMIN'],
  },
  {
    id: 2,
    username: 'zhangsan',
    realName: '张三',
    deptCode: 'D002',
    status: 1,
    roles: ['NORMAL_USER'],
  },
]
</script>

<template>
  <PageContainer title="用户管理" description="采用标准查询列表页模板，后续直接接入用户查询、创建、状态切换和角色分配接口。">
    <el-card class="idm-card" shadow="never">
      <el-form :inline="true" :model="query">
        <el-form-item label="账号">
          <el-input v-model="query.username" placeholder="请输入账号" clearable />
        </el-form-item>
        <el-form-item label="部门编码">
          <el-input v-model="query.deptCode" placeholder="请输入部门编码" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 160px">
            <el-option label="启用" value="1" />
            <el-option label="禁用" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="notifyPlanned('用户查询')">查询</el-button>
          <el-button @click="query.username = ''; query.deptCode = ''; query.status = ''">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="idm-card" shadow="never">
      <template #header>
        <div class="view-toolbar">
          <strong>用户列表</strong>
          <div class="view-toolbar__actions">
            <el-button type="primary" @click="notifyPlanned('新增用户')">新增用户</el-button>
            <el-button @click="notifyPlanned('飞书用户同步')">飞书同步</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows">
        <el-table-column prop="username" label="账号" min-width="160" />
        <el-table-column prop="realName" label="姓名" min-width="140" />
        <el-table-column prop="deptCode" label="部门编码" min-width="120" />
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-space wrap>
              <el-tag v-for="role in row.roles" :key="role" type="info">{{ role }}</el-tag>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="260" fixed="right">
          <template #default="{ row }">
            <el-space wrap>
              <el-button link type="primary" @click="notifyPlanned(`查看用户 ${row.username}`)">详情</el-button>
              <el-button link type="primary" @click="notifyPlanned(`编辑用户 ${row.username}`)">编辑</el-button>
              <el-button link type="warning" @click="notifyPlanned(`分配角色 ${row.username}`)">分配角色</el-button>
              <el-button link type="success" @click="notifyPlanned(`同步 LDAP ${row.username}`)">同步 LDAP</el-button>
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

.view-toolbar__actions {
  display: flex;
  gap: 12px;
}
</style>
