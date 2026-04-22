<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { assignUserRoles, createUser, deleteUser, fetchUserDetail, fetchUsers, resetUserPassword, syncUserToLdap, syncUsersFromFeishu, updateUser, updateUserStatus } from '@/api/modules/user'
import { fetchRoles } from '@/api/modules/role'
import { fetchDepartmentTree } from '@/api/modules/department'
import UserDetailDrawer from '@/views/user/components/UserDetailDrawer.vue'
import UserFormDrawer from '@/views/user/components/UserFormDrawer.vue'
import UserRoleDrawer from '@/views/user/components/UserRoleDrawer.vue'
import type { DepartmentTreeOption } from '@/types/department'
import type { CreateUserPayload, UpdateUserPayload, UserItem, UserListQuery } from '@/types/user'

const queryClient = useQueryClient()

const searchForm = reactive<{
  username: string
  deptCode: string
  status: number | undefined
}>({
  username: '',
  deptCode: '',
  status: undefined,
})

const appliedQuery = reactive<UserListQuery>({
  username: '',
  deptCode: '',
  status: undefined,
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

const usersQuery = useQuery({
  queryKey: computed(() => [
    'users',
    appliedQuery.username || '',
    appliedQuery.deptCode || '',
    appliedQuery.status ?? 'all',
  ]),
  queryFn: () =>
    fetchUsers({
      username: appliedQuery.username || undefined,
      deptCode: appliedQuery.deptCode || undefined,
      status: appliedQuery.status,
    }),
})

const rolesQuery = useQuery({
  queryKey: ['roles'],
  queryFn: fetchRoles,
})

const departmentsQuery = useQuery({
  queryKey: ['department-tree'],
  queryFn: fetchDepartmentTree,
})

const users = computed(() => usersQuery.data.value || [])
const roles = computed(() => rolesQuery.data.value || [])
const departmentOptions = computed<DepartmentTreeOption[]>(() =>
  buildDepartmentOptions(departmentsQuery.data.value || []),
)
const roleIdMapByCode = computed<Record<string, number>>(() =>
  roles.value.reduce<Record<string, number>>((accumulator, role) => {
    accumulator[role.roleCode] = role.id
    return accumulator
  }, {}),
)

const total = computed(() => users.value.length)
const pagedUsers = computed(() => {
  const start = (pagination.page - 1) * pagination.pageSize
  return users.value.slice(start, start + pagination.pageSize)
})

const createUserMutation = useMutation({
  mutationFn: (payload: CreateUserPayload) => createUser(payload),
  onSuccess: async (user) => {
    ElMessage.success(`用户 ${user.username} 创建成功`)
    formVisible.value = false
    await refreshUsers()
  },
})

const updateUserMutation = useMutation({
  mutationFn: ({ userId, payload }: { userId: number; payload: UpdateUserPayload }) => updateUser(userId, payload),
  onSuccess: async (user) => {
    ElMessage.success(`用户 ${user.username} 更新成功`)
    formVisible.value = false
    await refreshUsers()
  },
})

const assignRolesMutation = useMutation({
  mutationFn: ({ userId, roleIds }: { userId: number; roleIds: number[] }) => assignUserRoles(userId, { roleIds }),
  onSuccess: async () => {
    ElMessage.success('角色分配已保存')
    roleVisible.value = false
    await refreshUsers()
  },
})

const updateStatusMutation = useMutation({
  mutationFn: ({ userId, statusCode }: { userId: number; statusCode: number }) => updateUserStatus(userId, statusCode),
  onSuccess: async (_, variables) => {
    ElMessage.success(variables.statusCode === 1 ? '用户已启用' : '用户已禁用')
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

const resetPasswordMutation = useMutation({
  mutationFn: (userId: number) => resetUserPassword(userId),
})

const syncLdapMutation = useMutation({
  mutationFn: (userId: number) => syncUserToLdap(userId),
  onSuccess: async (user) => {
    ElMessage.success(`用户 ${user.username} 已同步到 LDAP`)
    await refreshUsers()
  },
})

const syncFeishuMutation = useMutation({
  mutationFn: syncUsersFromFeishu,
})

function buildDepartmentOptions(options: { deptCode: string; deptName: string; status: number; children: unknown[] }[]): DepartmentTreeOption[] {
  return options.map((item) => ({
    value: item.deptCode,
    label: `${item.deptName} (${item.deptCode})`,
    disabled: item.status !== 1,
    children: buildDepartmentOptions(item.children as never[]),
  }))
}

function normalizeText(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function applySearch() {
  appliedQuery.username = normalizeText(searchForm.username)
  appliedQuery.deptCode = normalizeText(searchForm.deptCode)
  appliedQuery.status = typeof searchForm.status === 'number' ? searchForm.status : undefined
  pagination.page = 1
}

function resetSearch() {
  searchForm.username = ''
  searchForm.deptCode = ''
  searchForm.status = undefined
  applySearch()
}

async function refreshUsers() {
  await queryClient.invalidateQueries({ queryKey: ['users'] })
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

function openRoleAssign(user: UserItem) {
  roleUser.value = user
  selectedRoleIds.value = user.roleCodes
    .map((roleCode) => roleIdMapByCode.value[roleCode])
    .filter((roleId): roleId is number => typeof roleId === 'number')
  roleVisible.value = true
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
  })
}

async function handleToggleStatus(user: UserItem) {
  const nextStatus = user.status === 1 ? 0 : 1
  const targetText = nextStatus === 1 ? '启用' : '禁用'

  try {
    await ElMessageBox.confirm(`确认${targetText}用户 ${user.realName}（${user.username}）吗？`, `${targetText}用户`, {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    })

    await updateStatusMutation.mutateAsync({
      userId: user.id,
      statusCode: nextStatus,
    })
  } catch {
    // 用户取消操作时不做额外处理。
  }
}

async function handleDelete(user: UserItem) {
  try {
    await ElMessageBox.confirm(
      `删除后会同时清理 LDAP 账号映射与登录能力，确认删除用户 ${user.realName}（${user.username}）吗？`,
      '删除用户',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )

    await deleteUserMutation.mutateAsync(user.id)
  } catch {
    // 用户取消操作时不做额外处理。
  }
}

async function handleResetPassword(user: UserItem) {
  try {
    await ElMessageBox.confirm(
      `确认将用户 ${user.realName}（${user.username}）的密码重置为系统默认密码吗？`,
      '重置密码',
      {
        type: 'warning',
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
      },
    )

    const result = await resetPasswordMutation.mutateAsync(user.id)

    await ElMessageBox.alert(
      `用户 ${user.username} 的新密码为：${result.resetPassword}`,
      '密码重置成功',
      {
        type: 'success',
        confirmButtonText: '我已知晓',
      },
    )
  } catch {
    // 用户取消操作时不做额外处理。
  }
}

async function handleSyncLdap(user: UserItem) {
  await syncLdapMutation.mutateAsync(user.id)
}

async function handleSyncFeishu() {
  const result = await syncFeishuMutation.mutateAsync()
  ElMessage.success(`飞书同步任务已触发，批次号：${result.batch.batchNo}`)
}

function statusText(status: number) {
  return status === 1 ? '启用' : '禁用'
}

function statusTagType(status: number) {
  return status === 1 ? 'success' : 'danger'
}
</script>

<template>
  <PageContainer
    title="用户管理"
    description="统一维护平台用户、LDAP 状态与角色绑定，优先支持查询、创建、编辑、角色分配和运维动作。"
  >
    <el-card class="idm-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" clearable placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="部门编码">
          <el-input v-model="searchForm.deptCode" clearable placeholder="请输入部门编码" />
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
            <strong>用户列表</strong>
            <span class="idm-muted">当前共 {{ total }} 条数据</span>
          </div>

          <div class="view-toolbar__actions">
            <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
            <el-button :icon="RefreshRight" :loading="syncFeishuMutation.isPending.value" @click="handleSyncFeishu">
              飞书同步
            </el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="usersQuery.isLoading.value || usersQuery.isFetching.value" :data="pagedUsers" border>
        <el-table-column prop="username" label="用户名" min-width="150" />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column prop="employeeNo" label="工号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="deptCode" label="部门编码" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="220" show-overflow-tooltip />
        <el-table-column prop="mobile" label="手机号" min-width="140" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-space wrap>
              <el-tag v-for="role in row.roleCodes.slice(0, 2)" :key="role" type="info">{{ role }}</el-tag>
              <el-tag v-if="row.roleCodes.length > 2" type="warning">+{{ row.roleCodes.length - 2 }}</el-tag>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column prop="ldapDn" label="LDAP DN" min-width="280" show-overflow-tooltip />
        <el-table-column label="操作" min-width="280" fixed="right">
          <template #default="{ row }">
            <el-space>
              <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
              <el-button link type="primary" @click="openEdit(row.id)">编辑</el-button>
              <el-button link type="warning" @click="openRoleAssign(row)">分配角色</el-button>

              <el-dropdown trigger="click">
                <el-button link type="info">
                  更多
                  <el-icon class="el-icon--right"><MoreFilled /></el-icon>
                </el-button>

                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="handleToggleStatus(row)">
                      {{ row.status === 1 ? '禁用用户' : '启用用户' }}
                    </el-dropdown-item>
                    <el-dropdown-item @click="handleResetPassword(row)">重置密码</el-dropdown-item>
                    <el-dropdown-item @click="handleSyncLdap(row)">同步 LDAP</el-dropdown-item>
                    <el-dropdown-item divided @click="handleDelete(row)">删除用户</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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

    <UserDetailDrawer v-model="detailVisible" :loading="detailLoading" :user="detailUser" />

    <UserFormDrawer
      v-model="formVisible"
      :mode="formMode"
      :loading="createUserMutation.isPending.value || updateUserMutation.isPending.value"
      :user="formUser"
      :role-options="roles"
      :department-options="departmentOptions"
      @submit="handleFormSubmit"
    />

    <UserRoleDrawer
      v-model="roleVisible"
      :loading="assignRolesMutation.isPending.value"
      :user="roleUser"
      :role-options="roles"
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
</style>
