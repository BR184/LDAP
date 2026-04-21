<script setup lang="ts">
import { ref } from 'vue'
import { notifyPlanned } from '@/utils/placeholder'

interface DepartmentNode {
  id: string
  label: string
  deptCode: string
  ancestorPath: string
  children?: DepartmentNode[]
}

const departmentTree: DepartmentNode[] = [
  {
    id: 'D001',
    label: '研发中心',
    deptCode: 'D001',
    ancestorPath: '/D001',
    children: [
      {
        id: 'D002',
        label: '平台研发',
        deptCode: 'D002',
        ancestorPath: '/D001/D002',
      },
    ],
  },
]

const currentDepartment = ref<DepartmentNode>(departmentTree[0])

function handleNodeClick(node: DepartmentNode) {
  currentDepartment.value = node
}
</script>

<template>
  <PageContainer title="部门管理" description="采用树加详情页模板，适合承载部门树、部门详情和 LDAP 分组同步。">
    <div class="department-layout">
      <el-card class="idm-card department-layout__tree" shadow="never">
        <template #header>
          <div class="view-toolbar">
            <strong>部门树</strong>
            <el-button type="primary" @click="notifyPlanned('新增部门')">新增部门</el-button>
          </div>
        </template>

        <el-tree
          :data="departmentTree"
          node-key="id"
          default-expand-all
          highlight-current
          @node-click="handleNodeClick"
        />
      </el-card>

      <el-card class="idm-card" shadow="never">
        <template #header>
          <div class="view-toolbar">
            <strong>部门详情</strong>
            <div class="view-toolbar__actions">
              <el-button @click="notifyPlanned('编辑部门')">编辑</el-button>
              <el-button type="success" @click="notifyPlanned('同步部门 LDAP')">同步 LDAP</el-button>
            </div>
          </div>
        </template>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="部门名称">{{ currentDepartment.label }}</el-descriptions-item>
          <el-descriptions-item label="部门编码">{{ currentDepartment.deptCode }}</el-descriptions-item>
          <el-descriptions-item label="祖先路径" :span="2">
            {{ currentDepartment.ancestorPath }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
    </div>
  </PageContainer>
</template>

<style scoped lang="scss">
.department-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
}

.department-layout__tree {
  min-height: 520px;
}

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
