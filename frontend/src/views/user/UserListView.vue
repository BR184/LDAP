<script setup lang="ts">
import axios from 'axios'
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Delete, MoreFilled, Plus, Search } from '@element-plus/icons-vue'
import {
  assignUserRoles,
  batchDeleteUsers,
  createUser,
  deleteUser,
  fetchUserDetail,
  fetchUsers,
  resetUserPassword,
  syncUserToLdap,
  updateUser,
  updateUserAccess,
} from '@/api/modules/user'
import { fetchDepartmentTree } from '@/api/modules/department'
import { fetchRoles } from '@/api/modules/role'
import { useAuthStore } from '@/stores/auth'
import UserDetailDrawer from '@/views/user/components/UserDetailDrawer.vue'
import UserFormDrawer from '@/views/user/components/UserFormDrawer.vue'
import UserRoleDrawer from '@/views/user/components/UserRoleDrawer.vue'
import PersistentTableScrollFrame from '@/components/table-scroll/PersistentTableScrollFrame.vue'
import type { DepartmentTreeNode, DepartmentTreeOption } from '@/types/department'
import type { CreateUserPayload, UpdateUserPayload, UserItem, UserListQuery } from '@/types/user'

const SUPER_ADMIN_ROLE_CODE = 'SUPER_ADMIN'
const SYSTEM_ADMIN_USER_ID = 'admin'

const queryClient = useQueryClient()
const authStore = useAuthStore()
const tableRef = ref<{ clearSelection?: () => void } | null>(null)

const searchForm = reactive({
  userId: '',
  deptName: '',
  accessAllowed: undefined as boolean | undefined,
})

const appliedQuery = reactive<UserListQuery>({
  userId: '',
  deptName: '',
  accessAllowed: undefined,
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailUser = ref<UserItem | null>(null)
const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formUser = ref<UserItem | null>(null)
const roleVisible = ref(false)
const roleUser = ref<UserItem | null>(null)
const selectedRoleIds = ref<number[]>([])
const expectedRoleIds = ref<number[]>([])
const roleAssignmentLoading = ref(false)
const selectedUsers = ref<UserItem[]>([])
const canCreateUser = computed(() =>
  authStore.can('USER_CREATE') && authStore.can('ROLE_READ') && authStore.can('DEPT_TREE'),
)
const canEditUser = computed(() =>
  authStore.can('USER_UPDATE') && authStore.can('ROLE_READ') && authStore.can('DEPT_TREE'),
)
const canAssignUserRoles = computed(() => authStore.can('USER_ROLE_ASSIGN') && authStore.can('ROLE_READ'))
const canResetUserPassword = computed(() => authStore.canAny(
  'USER_PASSWORD_RESET_ALL',
  'USER_PASSWORD_RESET_DIRECT',
  'USER_PASSWORD_RESET_TREE',
))
const needsRoleOptions = computed(() => canCreateUser.value || canEditUser.value || canAssignUserRoles.value)
const needsDepartmentOptions = computed(() => canCreateUser.value || canEditUser.value)

const usersQuery = useQuery({
  queryKey: computed(() => ['users', appliedQuery.userId || '', appliedQuery.deptName || '', appliedQuery.accessAllowed ?? 'all']),
  queryFn: () =>
    fetchUsers({
      userId: appliedQuery.userId || undefined,
      deptName: appliedQuery.deptName || undefined,
      accessAllowed: appliedQuery.accessAllowed,
    }),
})

const rolesQuery = useQuery({
  queryKey: ['roles'],
  queryFn: fetchRoles,
  enabled: needsRoleOptions,
})

const departmentsQuery = useQuery({
  queryKey: ['department-tree'],
  queryFn: fetchDepartmentTree,
  enabled: needsDepartmentOptions,
})

const users = computed(() => usersQuery.data.value || [])
const roles = computed(() => rolesQuery.data.value || [])
const activeRoles = computed(() => roles.value.filter((role) => role.status === 1))
const total = computed(() => users.value.length)
const pagedUsers = computed(() => {
  const start = (pagination.page - 1) * pagination.pageSize
  return users.value.slice(start, start + pagination.pageSize)
})

const departmentOptions = computed<DepartmentTreeOption[]>(() => buildDepartmentOptions(departmentsQuery.data.value || []))
const roleIdMapByCode = computed<Record<string, number>>(() =>
  roles.value.reduce<Record<string, number>>((accumulator, role) => {
    accumulator[role.roleCode] = role.id
    return accumulator
  }, {}),
)

const currentOperatorPermissionLevel = computed(() => {
  const currentUser = authStore.currentUser
  if (!currentUser) {
    return Number.MAX_SAFE_INTEGER
  }
  return resolvePermissionLevel(currentUser.roleCodes)
})

const isCurrentUserSuperAdmin = computed(() => authStore.currentUser?.roleCodes.includes(SUPER_ADMIN_ROLE_CODE) ?? false)
const canBatchDelete = computed(() =>
  selectedUsers.value.length > 0
  || !!(appliedQuery.userId || appliedQuery.deptName || typeof appliedQuery.accessAllowed === 'boolean'),
)
const selectedDeletableUsers = computed(() => selectedUsers.value.filter((user) => canDeleteUser(user) && !isSuperAdminUser(user)))
const deletableUsersByQuery = computed(() => users.value.filter((user) => canDeleteUser(user) && !isSuperAdminUser(user)))

function hasMoreActions() {
  return canResetUserPassword.value || authStore.canAny('USER_SYNC_LDAP', 'USER_DELETE')
}

const createUserMutation = useMutation({
  mutationFn: (payload: CreateUserPayload) => createUser(payload),
  onSuccess: async (user) => {
    ElMessage.success(`用户 ${user.userId} 创建成功`)
    formVisible.value = false
    await refreshUsers()
  },
})

const updateUserMutation = useMutation({
  mutationFn: ({ userId, payload }: { userId: number; payload: UpdateUserPayload }) => updateUser(userId, payload),
  onSuccess: async (user) => {
    ElMessage.success(`用户 ${user.userId} 更新成功`)
    formVisible.value = false
    await refreshUsers()
  },
})

const assignRolesMutation = useMutation({
  mutationFn: ({ userId, roleIds, baselineRoleIds }: { userId: number; roleIds: number[]; baselineRoleIds: number[] }) =>
    assignUserRoles(userId, { roleIds, expectedRoleIds: baselineRoleIds }),
  onSuccess: async () => {
    ElMessage.success('角色分配已保存')
    roleVisible.value = false
    await refreshUsers()
  },
  onError: async (error, variables) => {
    if (!axios.isAxiosError(error) || error.response?.status !== 409) {
      return
    }
    roleAssignmentLoading.value = true
    try {
      await loadCurrentRoleAssignment(variables.userId)
      await refreshUsers()
    } finally {
      roleAssignmentLoading.value = false
    }
  },
})

const updateAccessMutation = useMutation({
  mutationFn: ({ userId, accessAllowed }: { userId: number; accessAllowed: boolean }) => updateUserAccess(userId, accessAllowed),
  onSuccess: async (_, variables) => {
    ElMessage.success(variables.accessAllowed ? '已允许用户使用平台' : '已关闭用户平台使用权限')
    await refreshUsers()
  },
})

const deleteUserMutation = useMutation({
  mutationFn: (userId: number) => deleteUser(userId),
  onSuccess: async () => {
    ElMessage.success('用户已删除')
    await refreshUsers()
  },
})

const batchDeleteUsersMutation = useMutation({
  mutationFn: batchDeleteUsers,
  onSuccess: async (result) => {
    ElMessage.success(`已批量删除 ${result.deletedCount} 个用户`)
    selectedUsers.value = []
    tableRef.value?.clearSelection?.()
    await refreshUsers()
  },
})

const resetPasswordMutation = useMutation({
  mutationFn: (userId: number) => resetUserPassword(userId),
})

const syncLdapMutation = useMutation({
  mutationFn: (userId: number) => syncUserToLdap(userId),
  onSuccess: async (user) => {
    ElMessage.success(`用户 ${user.userId} 已同步到 LDAP`)
    await refreshUsers()
  },
})

function buildDepartmentOptions(nodes: DepartmentTreeNode[]): DepartmentTreeOption[] {
  return nodes.map((item) => ({
    value: item.deptCode,
    label: `${item.deptName} (${item.deptCode})`,
    disabled: item.status !== 1,
    children: buildDepartmentOptions(item.children || []),
  }))
}

function normalizeText(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function resolvePermissionLevel(roleCodes: string[]) {
  if (!roleCodes.length) {
    return Number.MAX_SAFE_INTEGER
  }
  const levels = roleCodes
    .map((roleCode) => roles.value.find((role) => role.roleCode === roleCode)?.permissionLevel)
    .filter((level): level is number => typeof level === 'number')
  return levels.length ? Math.min(...levels) : Number.MAX_SAFE_INTEGER
}

function canDeleteUser(user: UserItem) {
  if (isProtectedAdministrator(user)) {
    return false
  }
  if (user.id === authStore.currentUser?.id) {
    return false
  }
  if (isCurrentUserSuperAdmin.value) {
    return true
  }
  return user.permissionLevel > currentOperatorPermissionLevel.value
}

function isSuperAdminUser(user: UserItem) {
  return user.roleCodes.includes(SUPER_ADMIN_ROLE_CODE)
}

function isSystemAdminUser(user: UserItem) {
  return user.userId.toLowerCase() === SYSTEM_ADMIN_USER_ID
}

function isProtectedAdministrator(user: UserItem) {
  return isSystemAdminUser(user) || isSuperAdminUser(user)
}

function applySearch() {
  appliedQuery.userId = normalizeText(searchForm.userId)
  appliedQuery.deptName = normalizeText(searchForm.deptName)
  appliedQuery.accessAllowed = typeof searchForm.accessAllowed === 'boolean' ? searchForm.accessAllowed : undefined
  pagination.page = 1
}

function resetSearch() {
  searchForm.userId = ''
  searchForm.deptName = ''
  searchForm.accessAllowed = undefined
  applySearch()
}

async function refreshUsers() {
  await queryClient.invalidateQueries({ queryKey: ['users'] })
}

function handleSelectionChange(rows: UserItem[]) {
  selectedUsers.value = rows
}

function selectableUser(row: UserItem) {
  return canDeleteUser(row) && !isSuperAdminUser(row)
}

async function openDetail(userId: number) {
  detailVisible.value = true
  detailLoading.value = true
  detailUser.value = null
  try {
    detailUser.value = await fetchUserDetail(userId)
  } finally {
    detailLoading.value = false
  }
}

function openCreate() {
  formMode.value = 'create'
  formUser.value = null
  formVisible.value = true
}

async function openEdit(userId: number) {
  formMode.value = 'edit'
  formVisible.value = true
  formUser.value = await fetchUserDetail(userId)
}

function mapRoleCodesToIds(roleCodes: string[]) {
  return roleCodes
    .map((roleCode) => roleIdMapByCode.value[roleCode])
    .filter((roleId): roleId is number => typeof roleId === 'number')
}

async function loadCurrentRoleAssignment(userId: number) {
  const [user] = await Promise.all([
    fetchUserDetail(userId),
    rolesQuery.refetch(),
  ])
  const currentRoleIds = mapRoleCodesToIds(user.roleCodes)
  roleUser.value = user
  selectedRoleIds.value = currentRoleIds
  expectedRoleIds.value = [...currentRoleIds]
}

async function openRoleAssign(user: UserItem) {
  roleUser.value = user
  selectedRoleIds.value = mapRoleCodesToIds(user.roleCodes)
  expectedRoleIds.value = [...selectedRoleIds.value]
  roleVisible.value = true
  roleAssignmentLoading.value = true
  try {
    await loadCurrentRoleAssignment(user.id)
  } catch {
    roleVisible.value = false
  } finally {
    roleAssignmentLoading.value = false
  }
}

async function handleFormSubmit(payload: CreateUserPayload | UpdateUserPayload) {
  if (formMode.value === 'create') {
    await createUserMutation.mutateAsync(payload as CreateUserPayload)
    return
  }
  if (!formUser.value) {
    return
  }
  await updateUserMutation.mutateAsync({
    userId: formUser.value.id,
    payload: payload as UpdateUserPayload,
  })
}

async function handleRoleSubmit(roleIds: number[]) {
  if (!roleUser.value) {
    return
  }
  await assignRolesMutation.mutateAsync({
    userId: roleUser.value.id,
    roleIds,
    baselineRoleIds: expectedRoleIds.value,
  })
}

async function confirmAccessChange(user: UserItem, nextAccessAllowed: boolean) {
  const actionText = nextAccessAllowed ? '允许使用' : '关闭使用权限'
  try {
    await ElMessageBox.confirm(`确认${actionText} ${user.realName}（${user.userId}）吗？文件导入不会改变该管理员设置。`, actionText, {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    })
    await updateAccessMutation.mutateAsync({
      userId: user.id,
      accessAllowed: nextAccessAllowed,
    })
    return true
  } catch {
    return false
  }
}

async function handleDelete(user: UserItem) {
  if (!canDeleteUser(user)) {
    ElMessage.warning('当前用户无权删除该账号')
    return
  }
  try {
    await ElMessageBox.confirm(
      `删除后会同步清理 LDAP 账号映射与登录能力，确认删除用户 ${user.realName}（${user.userId}）吗？`,
      '删除用户',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )
    await deleteUserMutation.mutateAsync(user.id)
  } catch {
    // 用户取消时不处理。
  }
}

async function handleBatchDelete() {
  const hasSelectedRows = selectedUsers.value.length > 0
  const hasSearchQuery = !!(appliedQuery.userId || appliedQuery.deptName || typeof appliedQuery.accessAllowed === 'boolean')
  if (!hasSelectedRows && !hasSearchQuery) {
    return
  }

  const targetUsers = hasSelectedRows ? selectedDeletableUsers.value : deletableUsersByQuery.value
  if (!targetUsers.length) {
    ElMessage.warning('当前没有可批量删除的用户')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认批量删除 ${targetUsers.length} 个用户吗？删除后会同步清理 LDAP 账号映射、角色绑定与登录能力。`,
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

  await batchDeleteUsersMutation.mutateAsync({
    userIds: targetUsers.map((user) => user.id),
    userIdKeyword: hasSelectedRows ? undefined : appliedQuery.userId || undefined,
    deptNameKeyword: hasSelectedRows ? undefined : appliedQuery.deptName || undefined,
    accessAllowed: hasSelectedRows ? undefined : appliedQuery.accessAllowed,
  })
}

async function handleResetPassword(user: UserItem) {
  if (!user.canResetPassword) {
    ElMessage.warning('无权限重置该用户密码')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认将用户 ${user.realName}（${user.userId}）的密码重置为默认密码 123456 吗？`,
      '重置密码',
      {
        type: 'warning',
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
      },
    )
    await resetPasswordMutation.mutateAsync(user.id)
    ElMessage.success('密码已重置为默认密码 123456')
    await refreshUsers()
  } catch {
    // 用户取消时不处理。
  }
}

async function handleSyncLdap(user: UserItem) {
  await syncLdapMutation.mutateAsync(user.id)
}

</script>

<template>
  <PageContainer
    title="用户管理"
    description="统一维护用户资料、管理员准入、LDAP 同步与角色绑定。管理员关闭的准入设置不会被文件导入覆盖。"
  >
    <el-card class="idm-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="用户ID/工号/姓名">
          <el-input v-model="searchForm.userId" clearable placeholder="请输入用户ID、工号或姓名" />
        </el-form-item>
        <el-form-item label="部门名称">
          <el-input v-model="searchForm.deptName" clearable placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="允许使用">
          <el-select v-model="searchForm.accessAllowed" clearable placeholder="全部账号" style="width: 160px">
            <el-option label="允许使用" :value="true" />
            <el-option label="已关闭" :value="false" />
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
            <strong>用户列表</strong>
            <span class="idm-muted">当前共 {{ total }} 条数据</span>
          </div>

          <div class="view-toolbar__actions">
            <el-button v-if="canCreateUser" type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
            <el-button
              v-if="authStore.can('USER_BATCH_DELETE')"
              type="danger"
              plain
              :icon="Delete"
              :disabled="!canBatchDelete"
              :loading="batchDeleteUsersMutation.isPending.value"
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
          v-loading="usersQuery.isLoading.value || usersQuery.isFetching.value"
          :data="pagedUsers"
          border
          @selection-change="handleSelectionChange"
        >
        <el-table-column v-if="authStore.can('USER_BATCH_DELETE')" type="selection" width="52" :selectable="selectableUser" />
        <el-table-column prop="id" label="数据库ID" min-width="100" show-overflow-tooltip />
        <el-table-column prop="userId" label="用户ID" min-width="180" show-overflow-tooltip />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column prop="employeeNo" label="工号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="jobTitle" label="职务" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.jobTitle || '--' }}
          </template>
        </el-table-column>
        <el-table-column prop="mobile" label="手机号" min-width="140" />
        <el-table-column prop="directLeaderRaw" label="直属上级" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.directLeaderRaw || '--' }}
          </template>
        </el-table-column>
        <el-table-column prop="leaderRef" label="上级ID" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.leaderRef || '--' }}
          </template>
        </el-table-column>
        <el-table-column prop="departmentPath" label="主部门" min-width="240">
          <template #default="{ row }">
            {{ row.departmentPath || row.deptName || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="兼职部门" min-width="280">
          <template #default="{ row }">
            <div v-if="row.partTimeDepartments.length" class="department-path-list">
              <span v-for="department in row.partTimeDepartments" :key="department.deptCode">
                {{ department.departmentPath || department.deptName || '--' }}
              </span>
            </div>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column prop="intranetEmail" label="内网邮箱" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.intranetEmail || '--' }}
          </template>
        </el-table-column>
        <el-table-column prop="email" label="工作邮箱" min-width="220" show-overflow-tooltip />
        <el-table-column prop="accountStatus" label="账号状态" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.accountStatus || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="在职状态" min-width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.employmentStatus === 'RESIGNED' ? 'warning' : 'success'">
              {{ row.employmentStatus === 'RESIGNED' ? '离职' : '在职' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="允许使用" width="84" align="center">
          <template #default="{ row }">
            <el-switch
              v-if="authStore.can('USER_ACCESS_UPDATE')"
              :model-value="row.accessAllowed"
              inline-prompt
              :active-icon="Check"
              :inactive-icon="Close"
              aria-label="切换用户平台使用权限"
              :disabled="isSystemAdminUser(row)"
              :loading="updateAccessMutation.isPending.value"
              :before-change="() => confirmAccessChange(row, !row.accessAllowed)"
            />
            <el-tag v-else :type="row.accessAllowed ? 'success' : 'info'" effect="plain">
              {{ row.accessAllowed ? '允许' : '禁止' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-space v-if="row.roleCodes.length" wrap>
              <el-tag v-for="role in row.roleCodes.slice(0, 2)" :key="role" type="info">{{ role }}</el-tag>
              <el-tag v-if="row.roleCodes.length > 2" type="warning">+{{ row.roleCodes.length - 2 }}</el-tag>
            </el-space>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column prop="ldapDn" label="LDAP DN" min-width="320" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.ldapDn || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="280" fixed="right">
          <template #default="{ row }">
            <el-space>
              <el-button v-if="authStore.can('USER_DETAIL')" link type="primary" @click="openDetail(row.id)">详情</el-button>
              <el-button v-if="canEditUser && !isSystemAdminUser(row)" link type="primary" @click="openEdit(row.id)">编辑</el-button>
              <el-button v-if="canAssignUserRoles" link type="warning" @click="openRoleAssign(row)">分配角色</el-button>

              <el-dropdown v-if="hasMoreActions()" trigger="click">
                <el-button link type="info">
                  更多
                  <el-icon class="el-icon--right"><MoreFilled /></el-icon>
                </el-button>

                <template #dropdown>
                  <el-dropdown-menu>
                    <el-tooltip
                      v-if="canResetUserPassword"
                      :disabled="row.canResetPassword"
                      content="无权重置此用户密码"
                      placement="left"
                    >
                      <span class="dropdown-tooltip-wrap">
                        <el-dropdown-item
                          :disabled="!row.canResetPassword"
                          @click="handleResetPassword(row)"
                        >
                          重置密码
                        </el-dropdown-item>
                      </span>
                    </el-tooltip>
                    <el-dropdown-item v-if="authStore.can('USER_SYNC_LDAP')" @click="handleSyncLdap(row)">同步 LDAP</el-dropdown-item>
                    <el-dropdown-item v-if="authStore.can('USER_DELETE')" divided :disabled="!canDeleteUser(row)" @click="handleDelete(row)">
                      删除用户
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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

    <UserDetailDrawer v-model="detailVisible" :loading="detailLoading" :user="detailUser" />

    <UserFormDrawer
      v-model="formVisible"
      :mode="formMode"
      :loading="createUserMutation.isPending.value || updateUserMutation.isPending.value"
      :user="formUser"
      :role-options="activeRoles"
      :department-options="departmentOptions"
      @submit="handleFormSubmit"
    />

    <UserRoleDrawer
      v-model="roleVisible"
      :loading="roleAssignmentLoading || assignRolesMutation.isPending.value"
      :user="roleUser"
      :role-options="activeRoles"
      :role-ids="selectedRoleIds"
      @submit="handleRoleSubmit"
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

.view-toolbar__actions {
  display: flex;
  gap: 12px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.dropdown-tooltip-wrap {
  display: block;
}

.department-path-list {
  display: grid;
  gap: 4px;
  white-space: normal;
  line-height: 1.45;
}
</style>
