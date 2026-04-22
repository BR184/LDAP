<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { fetchMenuTree } from '@/api/modules/menu'
import { fetchPermissionTree } from '@/api/modules/permission'
import {
  bindRoleMenus,
  createRole,
  deleteRole,
  fetchRoleMenuIds,
  fetchRolePermissionIds,
  fetchRoles,
  grantRolePermissions,
  updateRole,
  updateRoleStatus,
} from '@/api/modules/role'
import RoleFormDrawer from '@/views/role/components/RoleFormDrawer.vue'
import RoleMenuDrawer from '@/views/role/components/RoleMenuDrawer.vue'
import RolePermissionDrawer from '@/views/role/components/RolePermissionDrawer.vue'
import type { MenuTreeNode } from '@/types/menu'
import type { PermissionTreeNode } from '@/types/permission'
import type { CreateRolePayload, RoleItem, UpdateRolePayload } from '@/types/role'

const queryClient = useQueryClient()

const searchForm = reactive<{
  roleCode: string
  status: number | undefined
}>({
  roleCode: '',
  status: undefined,
})

const appliedQuery = reactive({
  roleCode: '',
  status: undefined as number | undefined,
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
})

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const currentRole = ref<RoleItem | null>(null)

const menuDrawerVisible = ref(false)
const menuDrawerLoading = ref(false)
const menuDrawerRole = ref<RoleItem | null>(null)
const checkedMenuIds = ref<number[]>([])

const permissionDrawerVisible = ref(false)
const permissionDrawerLoading = ref(false)
const permissionDrawerRole = ref<RoleItem | null>(null)
const checkedPermissionIds = ref<number[]>([])

const rolesQuery = useQuery({
  queryKey: ['roles'],
  queryFn: fetchRoles,
})

const menuTreeQuery = useQuery({
  queryKey: ['menus', 'tree'],
  queryFn: fetchMenuTree,
})

const permissionTreeQuery = useQuery({
  queryKey: ['permissions', 'tree'],
  queryFn: fetchPermissionTree,
})

const createRoleMutation = useMutation({
  mutationFn: createRole,
  onSuccess: async (role) => {
    ElMessage.success(`角色 ${role.roleName} 创建成功`)
    formVisible.value = false
    await refreshRoles()
  },
})

const updateRoleMutation = useMutation({
  mutationFn: ({ roleId, payload }: { roleId: number; payload: UpdateRolePayload }) => updateRole(roleId, payload),
  onSuccess: async (role) => {
    ElMessage.success(`角色 ${role.roleName} 更新成功`)
    formVisible.value = false
    await refreshRoles()
  },
})

const updateRoleStatusMutation = useMutation({
  mutationFn: ({ roleId, status }: { roleId: number; status: number }) => updateRoleStatus(roleId, { status }),
  onSuccess: async (_, variables) => {
    ElMessage.success(variables.status === 1 ? '角色已启用' : '角色已禁用')
    await refreshRoles()
  },
})

const deleteRoleMutation = useMutation({
  mutationFn: (roleId: number) => deleteRole(roleId),
  onSuccess: async () => {
    ElMessage.success('角色已删除')
    await refreshRoles()
  },
})

const bindMenusMutation = useMutation({
  mutationFn: ({ roleId, menuIds }: { roleId: number; menuIds: number[] }) => bindRoleMenus(roleId, { menuIds }),
  onSuccess: async () => {
    ElMessage.success('菜单绑定已保存')
    menuDrawerVisible.value = false
    await refreshRoles()
  },
})

const grantPermissionsMutation = useMutation({
  mutationFn: ({ roleId, permissionIds }: { roleId: number; permissionIds: number[] }) =>
    grantRolePermissions(roleId, { permissionIds }),
  onSuccess: async () => {
    ElMessage.success('权限授权已保存')
    permissionDrawerVisible.value = false
    await refreshRoles()
  },
})

const roles = computed(() => rolesQuery.data.value || [])
const menuTree = computed<MenuTreeNode[]>(() => menuTreeQuery.data.value || [])
const permissionTree = computed<PermissionTreeNode[]>(() => permissionTreeQuery.data.value || [])

const filteredRoles = computed(() =>
  roles.value.filter((role) => {
    const matchesRoleCode = appliedQuery.roleCode
      ? role.roleCode.toLowerCase().includes(appliedQuery.roleCode.toLowerCase())
      : true
    const matchesStatus = typeof appliedQuery.status === 'number' ? role.status === appliedQuery.status : true
    return matchesRoleCode && matchesStatus
  }),
)

const total = computed(() => filteredRoles.value.length)
const pagedRoles = computed(() => {
  const start = (pagination.page - 1) * pagination.pageSize
  return filteredRoles.value.slice(start, start + pagination.pageSize)
})

function normalizeText(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : ''
}

function applySearch() {
  appliedQuery.roleCode = normalizeText(searchForm.roleCode)
  appliedQuery.status = typeof searchForm.status === 'number' ? searchForm.status : undefined
  pagination.page = 1
}

function resetSearch() {
  searchForm.roleCode = ''
  searchForm.status = undefined
  applySearch()
}

async function refreshRoles() {
  await queryClient.invalidateQueries({ queryKey: ['roles'] })
}

function openCreate() {
  formMode.value = 'create'
  currentRole.value = null
  formVisible.value = true
}

function openEdit(role: RoleItem) {
  formMode.value = 'edit'
  currentRole.value = { ...role }
  formVisible.value = true
}

async function openBindMenus(role: RoleItem) {
  menuDrawerRole.value = role
  checkedMenuIds.value = []
  menuDrawerVisible.value = true
  menuDrawerLoading.value = true

  try {
    checkedMenuIds.value = await fetchRoleMenuIds(role.id)
  } finally {
    menuDrawerLoading.value = false
  }
}

async function openGrantPermissions(role: RoleItem) {
  permissionDrawerRole.value = role
  checkedPermissionIds.value = []
  permissionDrawerVisible.value = true
  permissionDrawerLoading.value = true

  try {
    checkedPermissionIds.value = await fetchRolePermissionIds(role.id)
  } finally {
    permissionDrawerLoading.value = false
  }
}

async function handleSubmitRole(payload: CreateRolePayload | UpdateRolePayload) {
  if (formMode.value === 'create') {
    await createRoleMutation.mutateAsync(payload as CreateRolePayload)
    return
  }

  if (!currentRole.value) {
    return
  }

  await updateRoleMutation.mutateAsync({
    roleId: currentRole.value.id,
    payload: payload as UpdateRolePayload,
  })
}

async function handleToggleStatus(role: RoleItem) {
  const nextStatus = role.status === 1 ? 0 : 1
  const actionText = nextStatus === 1 ? '启用' : '禁用'

  try {
    await ElMessageBox.confirm(
      `确认${actionText}角色 ${role.roleName}（${role.roleCode}）吗？`,
      `${actionText}角色`,
      {
        type: 'warning',
        confirmButtonText: '确认',
        cancelButtonText: '取消',
      },
    )

    await updateRoleStatusMutation.mutateAsync({
      roleId: role.id,
      status: nextStatus,
    })
  } catch {
    // 用户取消操作时不做额外处理。
  }
}

async function handleDelete(role: RoleItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除角色 ${role.roleName}（${role.roleCode}）吗？删除后将同时清理该角色的菜单与权限绑定。`,
      '删除角色',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )

    await deleteRoleMutation.mutateAsync(role.id)
  } catch {
    // 用户取消操作时不做额外处理。
  }
}

async function handleBindMenus(menuIds: number[]) {
  if (!menuDrawerRole.value) {
    return
  }
  await bindMenusMutation.mutateAsync({
    roleId: menuDrawerRole.value.id,
    menuIds,
  })
}

async function handleGrantPermissions(permissionIds: number[]) {
  if (!permissionDrawerRole.value) {
    return
  }
  await grantPermissionsMutation.mutateAsync({
    roleId: permissionDrawerRole.value.id,
    permissionIds,
  })
}

function builtInText(builtIn: number) {
  return builtIn === 1 ? '内置' : '自定义'
}

function builtInTagType(builtIn: number) {
  return builtIn === 1 ? 'warning' : 'info'
}

function statusText(status: number) {
  return status === 1 ? '启用' : '禁用'
}

function statusTagType(status: number) {
  return status === 1 ? 'success' : 'danger'
}
</script>

<template>
  <PageContainer title="角色管理" description="维护角色基础信息、启停状态、菜单绑定和接口权限授权，形成完整的 RBAC 管理闭环。">
    <el-card class="idm-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="角色编码">
          <el-input v-model="searchForm.roleCode" clearable placeholder="请输入角色编码" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部状态" style="width: 160px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="applySearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="idm-card" shadow="never">
      <template #header>
        <div class="view-toolbar">
          <div class="view-toolbar__summary">
            <strong>角色列表</strong>
            <span class="idm-muted">当前共 {{ total }} 条数据</span>
          </div>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
        </div>
      </template>

      <el-table v-loading="rolesQuery.isLoading.value || rolesQuery.isFetching.value" :data="pagedRoles" border>
        <el-table-column prop="roleCode" label="角色编码" min-width="160" />
        <el-table-column prop="roleName" label="角色名称" min-width="160" />
        <el-table-column prop="permissionLevel" label="权限等级" width="120" align="center" />
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="builtInTagType(row.builtIn)">{{ builtInText(row.builtIn) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" min-width="340" fixed="right">
          <template #default="{ row }">
            <el-space wrap>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="warning" @click="openBindMenus(row)">绑定菜单</el-button>
              <el-button link type="success" @click="openGrantPermissions(row)">授权权限</el-button>
              <el-button link type="info" @click="handleToggleStatus(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button v-if="row.builtIn !== 1" link type="danger" @click="handleDelete(row)">删除</el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :background="true"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="total"
        />
      </div>
    </el-card>

    <RoleFormDrawer
      v-model="formVisible"
      :mode="formMode"
      :loading="createRoleMutation.isPending.value || updateRoleMutation.isPending.value"
      :role="currentRole"
      @submit="handleSubmitRole"
    />

    <RoleMenuDrawer
      v-model="menuDrawerVisible"
      :checked-menu-ids="checkedMenuIds"
      :initializing="menuDrawerLoading || menuTreeQuery.isLoading.value"
      :loading="bindMenusMutation.isPending.value"
      :menu-tree="menuTree"
      :role="menuDrawerRole"
      @submit="handleBindMenus"
    />

    <RolePermissionDrawer
      v-model="permissionDrawerVisible"
      :checked-permission-ids="checkedPermissionIds"
      :initializing="permissionDrawerLoading || permissionTreeQuery.isLoading.value"
      :loading="grantPermissionsMutation.isPending.value"
      :permission-tree="permissionTree"
      :role="permissionDrawerRole"
      @submit="handleGrantPermissions"
    />
  </PageContainer>
</template>

<style scoped lang="scss">
.view-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.view-toolbar__summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
