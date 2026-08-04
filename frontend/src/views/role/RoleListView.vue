<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Search } from '@element-plus/icons-vue'
import { fetchPermissionTree, fetchRolePermissionBundles } from '@/api/modules/permission'
import { fetchGovernedRoles, fetchRoleGroups, updateGovernedRoleScope } from '@/api/modules/role-group'
import { useAuthStore } from '@/stores/auth'
import {
  batchDeleteRoles,
  createRole,
  deleteRole,
  fetchRolePermissionIds,
  fetchRoles,
  grantRolePermissions,
  updateRole,
  updateRoleStatus,
} from '@/api/modules/role'
import RoleFormDrawer from '@/views/role/components/RoleFormDrawer.vue'
import RolePermissionDrawer from '@/views/role/components/RolePermissionDrawer.vue'
import PersistentTableScrollFrame from '@/components/table-scroll/PersistentTableScrollFrame.vue'
import type { PermissionTreeNode, RolePermissionBundle } from '@/types/permission'
import type { CreateRolePayload, RoleItem, UpdateRolePayload } from '@/types/role'
import type { GovernedRoleItem, RoleScope } from '@/types/role-group'

const SUPER_ADMIN_ROLE_CODE = 'SUPER_ADMIN'

const queryClient = useQueryClient()
const authStore = useAuthStore()
const tableRef = ref<{ clearSelection?: () => void } | null>(null)

const searchForm = reactive<{
  roleCode: string
  status: number | undefined
  roleScopes: RoleScope[]
}>({
  roleCode: '',
  status: undefined,
  roleScopes: [],
})

const appliedQuery = reactive({
  roleCode: '',
  status: undefined as number | undefined,
  roleScopes: [] as RoleScope[],
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
})

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const currentRole = ref<RoleItem | null>(null)

const permissionDrawerVisible = ref(false)
const permissionDrawerLoading = ref(false)
const permissionDrawerRole = ref<RoleItem | null>(null)
const checkedPermissionIds = ref<number[]>([])
const scopeDialogVisible = ref(false)
const scopeTargetRole = ref<GovernedRoleItem | null>(null)
const scopeTargetGroupId = ref<number | null>(null)

const selectedRoles = ref<RoleItem[]>([])

const rolesQuery = useQuery({
  queryKey: ['roles'],
  queryFn: fetchRoles,
  enabled: computed(() => !authStore.isAdmin),
})

const governedRolesQuery = useQuery({
  queryKey: ['role-governance', 'roles'],
  queryFn: fetchGovernedRoles,
  enabled: computed(() => authStore.isAdmin),
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

const batchDeleteRolesMutation = useMutation({
  mutationFn: batchDeleteRoles,
  onSuccess: async (result) => {
    ElMessage.success(`已批量删除 ${result.deletedCount} 个角色`)
    selectedRoles.value = []
    tableRef.value?.clearSelection?.()
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

const permissionBundlesQuery = useQuery({
  queryKey: ['permissions', 'bundles'],
  queryFn: fetchRolePermissionBundles,
})

const roleGroupsQuery = useQuery({
  queryKey: ['role-groups', 'scope-targets'],
  queryFn: fetchRoleGroups,
  enabled: computed(() => authStore.isAdmin),
})

const updateScopeMutation = useMutation({
  mutationFn: ({ roleId, roleScope, roleGroupId }: { roleId: number; roleScope: RoleScope; roleGroupId: number | null }) =>
    updateGovernedRoleScope(roleId, roleScope, roleGroupId),
  onSuccess: async () => {
    scopeDialogVisible.value = false
    ElMessage.success('角色作用域已更新')
    await refreshRoles()
  },
})

const roles = computed<Array<RoleItem | GovernedRoleItem>>(() =>
  authStore.isAdmin ? governedRolesQuery.data.value || [] : rolesQuery.data.value || [],
)
const permissionTree = computed<PermissionTreeNode[]>(() => permissionTreeQuery.data.value || [])
const permissionBundles = computed<RolePermissionBundle[]>(() => permissionBundlesQuery.data.value || [])
const hasSelectedRoles = computed(() => selectedRoles.value.length > 0)

const filteredRoles = computed(() =>
  roles.value.filter((role) => {
    const matchesRoleCode = appliedQuery.roleCode
      ? role.roleCode.toLowerCase().includes(appliedQuery.roleCode.toLowerCase())
      : true
    const matchesStatus = typeof appliedQuery.status === 'number' ? role.status === appliedQuery.status : true
    const matchesScope = !appliedQuery.roleScopes.length
      || ('roleScope' in role && appliedQuery.roleScopes.includes(role.roleScope))
    return matchesRoleCode && matchesStatus && matchesScope
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
  appliedQuery.roleScopes = [...searchForm.roleScopes]
  pagination.page = 1
}

function resetSearch() {
  searchForm.roleCode = ''
  searchForm.status = undefined
  searchForm.roleScopes = []
  applySearch()
}

async function refreshRoles() {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['roles'] }),
    queryClient.invalidateQueries({ queryKey: ['role-governance', 'roles'] }),
  ])
}

function handleSelectionChange(rows: RoleItem[]) {
  selectedRoles.value = rows
}

function selectableRole(row: RoleItem) {
  return row.roleCode !== SUPER_ADMIN_ROLE_CODE
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
    // 用户取消时不额外处理
  }
}

async function handleDelete(role: RoleItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除角色 ${role.roleName}（${role.roleCode}）吗？删除后将同步清理该角色的权限授权。`,
      '删除角色',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )

    await deleteRoleMutation.mutateAsync(role.id)
  } catch {
    // 用户取消时不额外处理
  }
}

async function handleBatchDelete() {
  if (!selectedRoles.value.length) {
    return
  }

  const previewRoles = selectedRoles.value.slice(0, 5).map((role) => `${role.roleName}（${role.roleCode}）`)
  const previewText = previewRoles.join('、')
  const moreCount = selectedRoles.value.length - previewRoles.length
  const summaryText = moreCount > 0 ? `${previewText} 等 ${selectedRoles.value.length} 个角色` : previewText

  try {
    await ElMessageBox.confirm(
      `删除后将同步清理角色权限授权和用户角色关系约束。确认批量删除以下角色吗？\n${summaryText}`,
      '确认批量删除',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  await batchDeleteRolesMutation.mutateAsync({
    roleIds: selectedRoles.value.map((role) => role.id),
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

async function handleScopeChange(role: GovernedRoleItem, roleScope: RoleScope) {
  if (roleScope === role.roleScope) return
  if (roleScope === 'GROUP') {
    scopeTargetRole.value = role
    scopeTargetGroupId.value = null
    scopeDialogVisible.value = true
    return
  }
  await updateScopeMutation.mutateAsync({ roleId: role.id, roleScope, roleGroupId: null })
}

async function confirmGroupScope() {
  if (!scopeTargetRole.value || scopeTargetGroupId.value === null) return
  await updateScopeMutation.mutateAsync({
    roleId: scopeTargetRole.value.id,
    roleScope: 'GROUP',
    roleGroupId: scopeTargetGroupId.value,
  })
}

function scopeText(scope: RoleScope) {
  if (scope === 'GLOBAL') return '全局'
  if (scope === 'GROUP') return '角色组'
  return '系统级'
}
</script>

<template>
  <PageContainer title="角色管理" description="维护角色基础信息、启停状态与 API、菜单显示权限授权，形成完整的 RBAC 管理闭环。">
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
        <el-form-item v-if="authStore.isAdmin" label="作用域">
          <el-select
            v-model="searchForm.roleScopes"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
            placeholder="全部作用域"
            style="width: 240px"
          >
            <el-option label="系统级" value="SYSTEM" />
            <el-option label="全局" value="GLOBAL" />
            <el-option label="角色组" value="GROUP" />
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
          <div class="view-toolbar__actions">
            <el-button type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
            <el-button
              type="danger"
              plain
              :icon="Delete"
              :disabled="!hasSelectedRoles"
              :loading="batchDeleteRolesMutation.isPending.value"
              @click="handleBatchDelete"
            >
              批量删除
            </el-button>
          </div>
        </div>
      </template>

      <PersistentTableScrollFrame>
        <el-table
          ref="tableRef"
          v-loading="rolesQuery.isLoading.value || governedRolesQuery.isLoading.value || rolesQuery.isFetching.value || governedRolesQuery.isFetching.value"
          :data="pagedRoles"
          border
          @selection-change="handleSelectionChange"
        >
        <el-table-column type="selection" width="52" :selectable="selectableRole" />
        <el-table-column prop="roleCode" label="角色编码" min-width="160" />
        <el-table-column prop="roleName" label="角色名称" min-width="160" />
        <el-table-column prop="permissionLevel" label="权限等级" width="120" align="center" />
        <el-table-column v-if="authStore.isAdmin" label="作用域" width="150" align="center">
          <template #default="{ row }">
            <el-select
              :model-value="row.roleScope"
              size="small"
              :loading="updateScopeMutation.isPending.value"
              @change="(value: RoleScope) => handleScopeChange(row, value)"
            >
              <el-option label="系统级" value="SYSTEM" />
              <el-option label="全局" value="GLOBAL" />
              <el-option :label="scopeText('GROUP')" value="GROUP" />
            </el-select>
          </template>
        </el-table-column>
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
              <el-button v-if="row.roleScope !== 'GROUP'" link type="success" @click="openGrantPermissions(row)">授权权限</el-button>
              <el-button link type="info" @click="handleToggleStatus(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button v-if="row.roleCode !== SUPER_ADMIN_ROLE_CODE" link type="danger" @click="handleDelete(row)">
                删除
              </el-button>
            </el-space>
          </template>
        </el-table-column>
        </el-table>
      </PersistentTableScrollFrame>

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

    <RolePermissionDrawer
      v-model="permissionDrawerVisible"
      :checked-permission-ids="checkedPermissionIds"
      :initializing="permissionDrawerLoading || permissionTreeQuery.isLoading.value"
      :loading="grantPermissionsMutation.isPending.value"
      :permission-tree="permissionTree"
      :permission-bundles="permissionBundles"
      :role="permissionDrawerRole"
      @submit="handleGrantPermissions"
    />

    <el-dialog v-model="scopeDialogVisible" title="选择目标角色组" width="520px">
      <p class="scope-dialog__summary">
        角色“{{ scopeTargetRole?.roleName }}”将变更为角色组作用域。该角色必须未绑定平台权限。
      </p>
      <el-form label-position="top">
        <el-form-item label="目标角色组" required>
          <el-select
            v-model="scopeTargetGroupId"
            filterable
            placeholder="请选择角色组"
            style="width: 100%"
            :loading="roleGroupsQuery.isLoading.value"
          >
            <el-option
              v-for="group in roleGroupsQuery.data.value || []"
              :key="group.id"
              :label="group.groupName"
              :value="group.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scopeDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="scopeTargetGroupId === null"
          :loading="updateScopeMutation.isPending.value"
          @click="confirmGroupScope"
        >
          确认变更
        </el-button>
      </template>
    </el-dialog>
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

.view-toolbar__actions {
  display: flex;
  gap: 12px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.scope-dialog__summary {
  margin: 0 0 18px;
  color: var(--idm-text-secondary);
  line-height: 1.7;
}
</style>
