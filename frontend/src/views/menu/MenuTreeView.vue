<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderAdd, Plus } from '@element-plus/icons-vue'
import {
  createMenu,
  deleteMenu,
  fetchMenuDetail,
  fetchMenuTree,
  updateMenu,
} from '@/api/modules/menu'
import MenuFormDrawer from '@/views/menu/components/MenuFormDrawer.vue'
import type { CreateMenuPayload, MenuItem, MenuTreeNode, MenuTreeOption, UpdateMenuPayload } from '@/types/menu'
import { normalizeMenuTreeForDisplay } from '@/utils/menu-tree'

const queryClient = useQueryClient()

const selectedMenuId = ref<number | undefined>()
const currentMenu = ref<MenuItem | null>(null)
const detailLoading = ref(false)
const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const parentMenu = ref<MenuItem | null>(null)

const menuTreeQuery = useQuery({
  queryKey: ['menus', 'tree'],
  queryFn: fetchMenuTree,
})

const createMenuMutation = useMutation({
  mutationFn: createMenu,
  onSuccess: async (menu) => {
    ElMessage.success(`菜单 ${menu.menuName} 创建成功`)
    formVisible.value = false
    await refreshMenus()
    await loadMenuDetail(menu.id)
  },
})

const updateMenuMutation = useMutation({
  mutationFn: ({ menuId, payload }: { menuId: number; payload: UpdateMenuPayload }) => updateMenu(menuId, payload),
  onSuccess: async (menu) => {
    ElMessage.success(`菜单 ${menu.menuName} 更新成功`)
    formVisible.value = false
    await refreshMenus()
    await loadMenuDetail(menu.id)
  },
})

const deleteMenuMutation = useMutation({
  mutationFn: (menuId: number) => deleteMenu(menuId),
  onSuccess: async () => {
    ElMessage.success('菜单已删除')
    selectedMenuId.value = undefined
    currentMenu.value = null
    await refreshMenus()
  },
})

const menuTree = computed(() => normalizeMenuTreeForDisplay(menuTreeQuery.data.value || []))
const menuOptions = computed<MenuTreeOption[]>(() => buildMenuOptions(menuTree.value))

function buildMenuOptions(nodes: MenuTreeNode[]): MenuTreeOption[] {
  return [
    {
      value: 0,
      label: '根菜单',
      children: [],
    },
    ...nodes.map((node) => ({
      value: node.id,
      label: `${node.menuName} (${node.menuCode})`,
      children: buildMenuOptions(node.children || []).filter((item) => item.value !== 0),
    })),
  ]
}

async function refreshMenus() {
  await queryClient.invalidateQueries({ queryKey: ['menus', 'tree'] })
}

async function loadMenuDetail(menuId: number) {
  selectedMenuId.value = menuId
  detailLoading.value = true
  currentMenu.value = null

  try {
    currentMenu.value = await fetchMenuDetail(menuId)
  } finally {
    detailLoading.value = false
  }
}

function openCreateRoot() {
  formMode.value = 'create'
  parentMenu.value = null
  formVisible.value = true
}

function openCreateChild() {
  if (!currentMenu.value) {
    return
  }
  formMode.value = 'create'
  parentMenu.value = currentMenu.value
  formVisible.value = true
}

function openEdit() {
  if (!currentMenu.value) {
    return
  }
  formMode.value = 'edit'
  parentMenu.value = null
  formVisible.value = true
}

async function handleDelete() {
  if (!currentMenu.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认删除菜单 ${currentMenu.value.menuName}（${currentMenu.value.menuCode}）吗？`,
      '删除菜单',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )

    await deleteMenuMutation.mutateAsync(currentMenu.value.id)
  } catch {
    // 用户取消时不额外处理
  }
}

async function handleSubmit(payload: CreateMenuPayload | UpdateMenuPayload) {
  if (formMode.value === 'create') {
    await createMenuMutation.mutateAsync(payload as CreateMenuPayload)
    return
  }

  if (!currentMenu.value) {
    return
  }

  await updateMenuMutation.mutateAsync({
    menuId: currentMenu.value.id,
    payload: payload as UpdateMenuPayload,
  })
}

function menuTypeText(menuType: string) {
  return menuType === 'CATALOG' ? '目录' : '菜单'
}

function canCreateChild(menu: MenuItem | null) {
  return menu?.menuType === 'CATALOG'
}
</script>

<template>
  <PageContainer title="菜单管理" description="维护后台菜单树、导航挂载关系和菜单元数据，采用树加详情的页面组织方式。">
    <template #extra>
      <el-button type="primary" :icon="Plus" @click="openCreateRoot">新增根菜单</el-button>
    </template>

    <div class="menu-layout">
      <el-card class="idm-card menu-layout__tree" shadow="never">
        <template #header>
          <div class="view-toolbar">
            <strong>菜单树</strong>
            <span class="idm-muted">选择左侧节点查看详情</span>
          </div>
        </template>

        <el-tree
          v-loading="menuTreeQuery.isLoading.value || menuTreeQuery.isFetching.value"
          :current-node-key="selectedMenuId"
          :data="menuTree"
          default-expand-all
          highlight-current
          node-key="id"
          :props="{ label: 'menuName', children: 'children' }"
          @node-click="(node: MenuTreeNode) => loadMenuDetail(node.id)"
        >
          <template #default="{ data }">
            <div class="menu-tree-node">
              <strong>{{ data.menuName }}</strong>
              <span>{{ data.menuCode }}</span>
            </div>
          </template>
        </el-tree>
      </el-card>

      <el-card class="idm-card" shadow="never">
        <template #header>
          <div class="view-toolbar">
            <strong>菜单详情</strong>
            <div v-if="currentMenu" class="view-toolbar__actions">
              <el-button
                v-if="canCreateChild(currentMenu)"
                type="primary"
                plain
                :icon="FolderAdd"
                @click="openCreateChild"
              >
                新增子菜单
              </el-button>
              <el-button @click="openEdit">编辑</el-button>
              <el-button type="danger" plain @click="handleDelete">删除</el-button>
            </div>
          </div>
        </template>

        <el-skeleton :loading="detailLoading" animated :rows="8">
          <template #template>
            <el-skeleton-item variant="p" style="width: 100%; height: 24px" />
          </template>

          <template v-if="currentMenu">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="菜单编码">{{ currentMenu.menuCode }}</el-descriptions-item>
              <el-descriptions-item label="菜单名称">{{ currentMenu.menuName }}</el-descriptions-item>
              <el-descriptions-item label="菜单类型">{{ menuTypeText(currentMenu.menuType) }}</el-descriptions-item>
              <el-descriptions-item label="父菜单 ID">
                {{ currentMenu.parentId === 0 ? '根菜单' : currentMenu.parentId }}
              </el-descriptions-item>
              <el-descriptions-item label="菜单路由" :span="2">{{ currentMenu.path }}</el-descriptions-item>
              <el-descriptions-item label="组件路径" :span="2">{{ currentMenu.component || '--' }}</el-descriptions-item>
              <el-descriptions-item label="图标">{{ currentMenu.icon || '--' }}</el-descriptions-item>
              <el-descriptions-item label="排序值">{{ currentMenu.sortNo }}</el-descriptions-item>
              <el-descriptions-item label="备注" :span="2">{{ currentMenu.remark || '--' }}</el-descriptions-item>
            </el-descriptions>
          </template>

          <el-empty v-else description="请先从左侧选择菜单节点" />
        </el-skeleton>
      </el-card>
    </div>

    <MenuFormDrawer
      v-model="formVisible"
      :loading="createMenuMutation.isPending.value || updateMenuMutation.isPending.value"
      :menu="formMode === 'edit' ? currentMenu : null"
      :menu-options="menuOptions"
      :mode="formMode"
      :parent-menu="formMode === 'create' ? parentMenu : null"
      @submit="handleSubmit"
    />
  </PageContainer>
</template>

<style scoped lang="scss">
.menu-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
}

.menu-layout__tree {
  min-height: 560px;
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

.menu-tree-node {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.menu-tree-node span {
  color: var(--idm-text-secondary);
  font-size: 12px;
}
</style>
