<script setup lang="ts">
import { ref } from 'vue'
import { notifyPlanned } from '@/utils/placeholder'

interface MenuNode {
  id: number
  label: string
  path: string
  menuType: string
  children?: MenuNode[]
}

const menuTree: MenuNode[] = [
  {
    id: 1,
    label: '身份管理',
    path: '/identity',
    menuType: 'CATALOG',
    children: [
      {
        id: 2,
        label: '用户管理',
        path: '/users',
        menuType: 'MENU',
      },
      {
        id: 3,
        label: '部门管理',
        path: '/departments',
        menuType: 'MENU',
      },
    ],
  },
]

const currentMenu = ref<MenuNode>(menuTree[0])

function handleNodeClick(node: MenuNode) {
  currentMenu.value = node
}
</script>

<template>
  <PageContainer title="菜单管理" description="菜单页用于维护后台导航结构，适合采用树加详情的页面组织方式。">
    <div class="menu-layout">
      <el-card class="idm-card" shadow="never">
        <template #header>
          <div class="view-toolbar">
            <strong>菜单树</strong>
            <el-button type="primary" @click="notifyPlanned('新增菜单')">新增菜单</el-button>
          </div>
        </template>

        <el-tree
          :data="menuTree"
          node-key="id"
          default-expand-all
          highlight-current
          @node-click="handleNodeClick"
        />
      </el-card>

      <el-card class="idm-card" shadow="never">
        <template #header>
          <div class="view-toolbar">
            <strong>菜单详情</strong>
            <div class="view-toolbar__actions">
              <el-button @click="notifyPlanned('编辑菜单')">编辑</el-button>
              <el-button type="danger" plain @click="notifyPlanned('删除菜单')">删除</el-button>
            </div>
          </div>
        </template>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="菜单名称">{{ currentMenu.label }}</el-descriptions-item>
          <el-descriptions-item label="菜单类型">{{ currentMenu.menuType }}</el-descriptions-item>
          <el-descriptions-item label="菜单路径" :span="2">{{ currentMenu.path }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </div>
  </PageContainer>
</template>

<style scoped lang="scss">
.menu-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
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
