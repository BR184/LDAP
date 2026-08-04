<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, CopyDocument, Delete, Edit, Key, Plus, RefreshRight, UserFilled } from '@element-plus/icons-vue'
import PersistentTableScrollFrame from '@/components/table-scroll/PersistentTableScrollFrame.vue'
import { verifyMyPassword } from '@/api/modules/user'
import {
  addRoleMembers,
  createGlobalToken,
  createGroupToken,
  createRoleGroup,
  createRoleGroupRole,
  deleteGlobalToken,
  deleteGroupToken,
  deleteRoleGroup,
  deleteRoleGroupRole,
  fetchGlobalTokens,
  fetchGroupTokens,
  fetchRoleGroupCollaborators,
  fetchRoleGroupRoles,
  fetchRoleGroups,
  fetchRoleMembers,
  removeRoleGroupCollaborator,
  removeRoleMember,
  revealGlobalToken,
  revealGroupToken,
  revokeGlobalToken,
  revokeGroupToken,
  rotateGlobalToken,
  rotateGroupToken,
  saveRoleGroupCollaborator,
  searchRoleGroupUsers,
  updateRoleGroup,
  updateRoleGroupRole,
} from '@/api/modules/role-group'
import { useAuthStore } from '@/stores/auth'
import type {
  PublicUserItem,
  RoleGroupItem,
  RoleGroupMemberRole,
  RoleGroupRoleItem,
  RoleSupplyTokenItem,
} from '@/types/role-group'

const queryClient = useQueryClient()
const authStore = useAuthStore()
const selectedGroupId = ref<number | null>(null)
const activeTab = ref('roles')
const viewerId = computed(() => authStore.currentUser?.id ?? null)
const canReadRoleGroups = computed(() => authStore.canAny('ROLE_GROUP_READ', 'ROLE_GROUP_MANAGE'))
const hasRoleGroupManagement = computed(() => authStore.can('ROLE_GROUP_MANAGE'))

const groupsQuery = useQuery({
  queryKey: computed(() => ['role-groups', viewerId.value]),
  queryFn: fetchRoleGroups,
  enabled: computed(() => viewerId.value !== null && canReadRoleGroups.value),
})
const groups = computed(() => groupsQuery.data.value || [])
const selectedGroup = computed(() => groups.value.find((group) => group.id === selectedGroupId.value) || null)
const hasValidGroupSelection = computed(
  () => groupsQuery.isSuccess.value && selectedGroup.value !== null,
)
const canManageGroupSettings = computed(
  () => hasRoleGroupManagement.value && (authStore.isAdmin || selectedGroup.value?.currentMemberRole === 'OWNER'),
)
const canManageGroupContent = computed(() =>
  hasRoleGroupManagement.value
  && (
    authStore.isAdmin
    || selectedGroup.value?.currentMemberRole === 'OWNER'
    || selectedGroup.value?.currentMemberRole === 'MANAGER'
  ),
)
const canAssignRoleMembers = computed(() =>
  canManageGroupContent.value && authStore.can('ROLE_GROUP_USER_ASSIGN'),
)
const canManageGlobalTokens = computed(() => authStore.isAdmin && hasRoleGroupManagement.value)

const rolesQuery = useQuery({
  queryKey: computed(() => ['role-groups', viewerId.value, selectedGroupId.value, 'roles']),
  queryFn: () => fetchRoleGroupRoles(selectedGroupId.value!),
  enabled: hasValidGroupSelection,
})
const collaboratorsQuery = useQuery({
  queryKey: computed(() => ['role-groups', viewerId.value, selectedGroupId.value, 'collaborators']),
  queryFn: () => fetchRoleGroupCollaborators(selectedGroupId.value!),
  enabled: hasValidGroupSelection,
})
const groupTokensQuery = useQuery({
  queryKey: computed(() => ['role-groups', viewerId.value, selectedGroupId.value, 'tokens']),
  queryFn: () => fetchGroupTokens(selectedGroupId.value!),
  enabled: computed(() => hasValidGroupSelection.value && canManageGroupSettings.value),
})
const globalTokensQuery = useQuery({
  queryKey: computed(() => ['role-supply-tokens', viewerId.value, 'global']),
  queryFn: fetchGlobalTokens,
  enabled: computed(() => viewerId.value !== null && canManageGlobalTokens.value),
})

watch(
  viewerId,
  () => {
    selectedGroupId.value = null
    activeTab.value = canManageGlobalTokens.value ? 'global-tokens' : 'roles'
  },
  { immediate: true },
)

watch(
  groups,
  (items) => {
    if (!items.length) {
      selectedGroupId.value = null
      activeTab.value = canManageGlobalTokens.value ? 'global-tokens' : 'roles'
      return
    }
    if (!items.some((group) => group.id === selectedGroupId.value)) {
      selectedGroupId.value = items[0]?.id ?? null
    }
  },
  { immediate: true },
)

const groupDialogVisible = ref(false)
const groupDialogMode = ref<'create' | 'edit'>('create')
const groupForm = reactive({ groupName: '', remark: '' })

const roleDialogVisible = ref(false)
const roleDialogMode = ref<'create' | 'edit'>('create')
const editingRole = ref<RoleGroupRoleItem | null>(null)
const roleForm = reactive({ roleCode: '', roleName: '', remark: '' })

const collaboratorDialogVisible = ref(false)
const collaboratorForm = reactive<{ userId: number | null; memberRole: RoleGroupMemberRole }>({
  userId: null,
  memberRole: 'MANAGER',
})
const publicUserOptions = ref<PublicUserItem[]>([])
const publicUserLoading = ref(false)

const roleMembersVisible = ref(false)
const memberRole = ref<RoleGroupRoleItem | null>(null)
const roleMembers = ref<PublicUserItem[]>([])
const selectedMemberUserIds = ref<number[]>([])
const roleMembersLoading = ref(false)

const tokenDialogVisible = ref(false)
const tokenSubject = ref<'GROUP' | 'GLOBAL'>('GROUP')
const tokenForm = reactive<{ name: string; description: string; expiresAt: Date | null }>({
  name: '',
  description: '',
  expiresAt: null,
})
const secretDialogVisible = ref(false)
const currentSecret = ref('')

const refreshGroups = () => queryClient.invalidateQueries({
  queryKey: ['role-groups', viewerId.value],
  exact: true,
})
const refreshRoles = () => queryClient.invalidateQueries({
  queryKey: ['role-groups', viewerId.value, selectedGroupId.value, 'roles'],
  exact: true,
})
const refreshCollaborators = () =>
  queryClient.invalidateQueries({
    queryKey: ['role-groups', viewerId.value, selectedGroupId.value, 'collaborators'],
    exact: true,
  })
const refreshGroupTokens = () =>
  queryClient.invalidateQueries({
    queryKey: ['role-groups', viewerId.value, selectedGroupId.value, 'tokens'],
    exact: true,
  })
const refreshGlobalTokens = () => queryClient.invalidateQueries({
  queryKey: ['role-supply-tokens', viewerId.value, 'global'],
  exact: true,
})

const saveGroupMutation = useMutation({
  mutationFn: () =>
    groupDialogMode.value === 'create'
      ? createRoleGroup({ groupName: groupForm.groupName, remark: groupForm.remark || null })
      : updateRoleGroup(selectedGroupId.value!, { groupName: groupForm.groupName, remark: groupForm.remark || null }),
  onSuccess: async (group) => {
    groupDialogVisible.value = false
    selectedGroupId.value = group.id
    await refreshGroups()
    ElMessage.success(groupDialogMode.value === 'create' ? '角色组已创建' : '角色组设置已保存')
  },
})

const saveRoleMutation = useMutation({
  mutationFn: () =>
    roleDialogMode.value === 'create'
      ? createRoleGroupRole(selectedGroupId.value!, {
          roleCode: roleForm.roleCode,
          roleName: roleForm.roleName,
          remark: roleForm.remark || null,
        })
      : updateRoleGroupRole(selectedGroupId.value!, editingRole.value!.id, {
          roleName: roleForm.roleName,
          remark: roleForm.remark || null,
        }),
  onSuccess: async () => {
    roleDialogVisible.value = false
    await Promise.all([refreshRoles(), refreshGroups()])
    ElMessage.success(roleDialogMode.value === 'create' ? '组角色已创建' : '角色信息已保存')
  },
})

const saveCollaboratorMutation = useMutation({
  mutationFn: () =>
    saveRoleGroupCollaborator(
      selectedGroupId.value!,
      collaboratorForm.userId!,
      collaboratorForm.memberRole,
    ),
  onSuccess: async () => {
    collaboratorDialogVisible.value = false
    await Promise.all([refreshCollaborators(), refreshGroups()])
    ElMessage.success('协作成员已保存')
  },
})

const createTokenMutation = useMutation({
  mutationFn: () => {
    const payload = {
      name: tokenForm.name,
      description: tokenForm.description || null,
      expiresAt: tokenForm.expiresAt ? toLocalDateTime(tokenForm.expiresAt) : null,
    }
    return tokenSubject.value === 'GLOBAL'
      ? createGlobalToken(payload)
      : createGroupToken(selectedGroupId.value!, payload)
  },
  onSuccess: async (created) => {
    tokenDialogVisible.value = false
    showSecret(created.secret)
    await (tokenSubject.value === 'GLOBAL' ? refreshGlobalTokens() : refreshGroupTokens())
    ElMessage.success('供给令牌已创建')
  },
})

function selectGroup(group: RoleGroupItem) {
  selectedGroupId.value = group.id
  activeTab.value = 'roles'
}

function openCreateGroup() {
  groupDialogMode.value = 'create'
  groupForm.groupName = ''
  groupForm.remark = ''
  groupDialogVisible.value = true
}

function openEditGroup() {
  if (!selectedGroup.value) return
  groupDialogMode.value = 'edit'
  groupForm.groupName = selectedGroup.value.groupName
  groupForm.remark = selectedGroup.value.remark || ''
  groupDialogVisible.value = true
}

async function handleDeleteGroup() {
  if (!selectedGroup.value) return
  await ElMessageBox.confirm(`确认删除角色组“${selectedGroup.value.groupName}”吗？`, '删除角色组', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteRoleGroup(selectedGroup.value.id)
  await refreshGroups()
  ElMessage.success('角色组已删除')
}

function openCreateRole() {
  roleDialogMode.value = 'create'
  editingRole.value = null
  Object.assign(roleForm, { roleCode: '', roleName: '', remark: '' })
  roleDialogVisible.value = true
}

function openEditRole(role: RoleGroupRoleItem) {
  roleDialogMode.value = 'edit'
  editingRole.value = role
  Object.assign(roleForm, { roleCode: role.roleCode, roleName: role.roleName, remark: role.remark || '' })
  roleDialogVisible.value = true
}

async function handleDeleteRole(role: RoleGroupRoleItem) {
  await ElMessageBox.confirm(`确认删除组角色“${role.roleName}”吗？`, '删除角色', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteRoleGroupRole(selectedGroupId.value!, role.id)
  await Promise.all([refreshRoles(), refreshGroups()])
  ElMessage.success('组角色已删除')
}

async function searchPublicUsers(keyword: string) {
  if (!selectedGroupId.value || !keyword.trim()) {
    publicUserOptions.value = []
    return
  }
  publicUserLoading.value = true
  try {
    publicUserOptions.value = await searchRoleGroupUsers(selectedGroupId.value, keyword.trim())
  } finally {
    publicUserLoading.value = false
  }
}

function openCollaboratorDialog() {
  collaboratorForm.userId = null
  collaboratorForm.memberRole = 'MANAGER'
  publicUserOptions.value = []
  collaboratorDialogVisible.value = true
}

async function removeCollaborator(userId: number, realName: string) {
  await ElMessageBox.confirm(`确认移除协作成员“${realName}”吗？`, '移除协作成员', {
    type: 'warning',
    confirmButtonText: '移除',
    cancelButtonText: '取消',
  })
  await removeRoleGroupCollaborator(selectedGroupId.value!, userId)
  await Promise.all([refreshCollaborators(), refreshGroups()])
}

async function openRoleMembers(role: RoleGroupRoleItem) {
  memberRole.value = role
  roleMembersVisible.value = true
  selectedMemberUserIds.value = []
  publicUserOptions.value = []
  await reloadRoleMembers()
}

async function reloadRoleMembers() {
  if (!selectedGroupId.value || !memberRole.value) return
  roleMembersLoading.value = true
  try {
    roleMembers.value = await fetchRoleMembers(selectedGroupId.value, memberRole.value.id)
  } finally {
    roleMembersLoading.value = false
  }
}

async function applyRoleMembers() {
  if (!selectedMemberUserIds.value.length || !memberRole.value) return
  await addRoleMembers(selectedGroupId.value!, memberRole.value.id, selectedMemberUserIds.value)
  selectedMemberUserIds.value = []
  await reloadRoleMembers()
  ElMessage.success('角色成员已添加')
}

async function deleteRoleMember(user: PublicUserItem) {
  if (!memberRole.value) return
  await removeRoleMember(selectedGroupId.value!, memberRole.value.id, user.id)
  await reloadRoleMembers()
}

function openTokenDialog(subject: 'GROUP' | 'GLOBAL') {
  tokenSubject.value = subject
  Object.assign(tokenForm, { name: '', description: '', expiresAt: null })
  tokenDialogVisible.value = true
}

async function rotateToken(token: RoleSupplyTokenItem, subject: 'GROUP' | 'GLOBAL') {
  await ElMessageBox.confirm('轮换后旧令牌立即失效，是否继续？', '轮换令牌', {
    type: 'warning',
    confirmButtonText: '轮换',
    cancelButtonText: '取消',
  })
  const created = subject === 'GLOBAL'
    ? await rotateGlobalToken(token.id)
    : await rotateGroupToken(selectedGroupId.value!, token.id)
  showSecret(created.secret)
  await (subject === 'GLOBAL' ? refreshGlobalTokens() : refreshGroupTokens())
}

async function revealToken(token: RoleSupplyTokenItem, subject: 'GROUP' | 'GLOBAL') {
  const prompt = await ElMessageBox.prompt('请输入当前登录密码', '查看完整令牌', {
    inputType: 'password',
    inputPattern: /\S+/,
    inputErrorMessage: '请输入当前登录密码',
    confirmButtonText: '验证并查看',
    cancelButtonText: '取消',
  })
  const verification = await verifyMyPassword({ oldPassword: prompt.value })
  const result = subject === 'GLOBAL'
    ? await revealGlobalToken(token.id, verification.verificationToken)
    : await revealGroupToken(selectedGroupId.value!, token.id, verification.verificationToken)
  showSecret(result.secret)
}

async function revokeToken(token: RoleSupplyTokenItem, subject: 'GROUP' | 'GLOBAL') {
  await ElMessageBox.confirm('撤销后令牌立即失效，是否继续？', '撤销令牌', {
    type: 'warning',
    confirmButtonText: '撤销',
    cancelButtonText: '取消',
  })
  if (subject === 'GLOBAL') await revokeGlobalToken(token.id)
  else await revokeGroupToken(selectedGroupId.value!, token.id)
  await (subject === 'GLOBAL' ? refreshGlobalTokens() : refreshGroupTokens())
}

async function removeToken(token: RoleSupplyTokenItem, subject: 'GROUP' | 'GLOBAL') {
  await ElMessageBox.confirm(`确认删除令牌“${token.name}”吗？`, '删除令牌', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  if (subject === 'GLOBAL') await deleteGlobalToken(token.id)
  else await deleteGroupToken(selectedGroupId.value!, token.id)
  await (subject === 'GLOBAL' ? refreshGlobalTokens() : refreshGroupTokens())
}

function showSecret(secret: string) {
  currentSecret.value = secret
  secretDialogVisible.value = true
}

async function copySecret() {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(currentSecret.value)
  } else {
    const input = document.createElement('textarea')
    input.value = currentSecret.value
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    input.remove()
  }
  ElMessage.success('令牌已复制')
}

function toLocalDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

function scopeText(scope: string) {
  return scope === 'GLOBAL' ? '全局角色' : '组角色'
}

function memberRoleText(role: RoleGroupMemberRole | null) {
  return role === 'OWNER' ? '所有者' : role === 'MANAGER' ? '协管员' : '未加入'
}

function accessIdentityText(group: RoleGroupItem) {
  if (group.currentMemberRole) return `我的身份：${memberRoleText(group.currentMemberRole)}`
  return authStore.isAdmin ? '访问身份：平台管理员监督' : '我的身份：未加入'
}

function railIdentityText(group: RoleGroupItem) {
  if (group.currentMemberRole) return memberRoleText(group.currentMemberRole)
  return authStore.isAdmin ? '平台监管' : '未加入'
}

function ownerNamesText(group: RoleGroupItem) {
  return group.ownerNames.length ? group.ownerNames.join('、') : '暂无所有者'
}

function tokenStatusText(status: string) {
  return status === 'ACTIVE' ? '有效' : status === 'REVOKED' ? '已撤销' : '已过期'
}
</script>

<template>
  <PageContainer title="角色组管理">
    <div class="role-group-workspace">
      <aside class="group-rail">
        <div class="group-rail__header">
          <div>
            <strong>角色组</strong>
            <span>{{ groups.length }}</span>
          </div>
          <el-button v-if="hasRoleGroupManagement" circle type="primary" :icon="Plus" title="创建角色组" @click="openCreateGroup" />
        </div>
        <el-skeleton v-if="groupsQuery.isLoading.value" :rows="5" animated />
        <el-empty v-else-if="!groups.length" description="暂无角色组" :image-size="72" />
        <div v-else class="group-list">
          <button
            v-for="group in groups"
            :key="group.id"
            type="button"
            class="group-list__item"
            :class="{ 'is-active': group.id === selectedGroupId }"
            @click="selectGroup(group)"
          >
            <span class="group-list__headline">
              <span class="group-list__name">{{ group.groupName }}</span>
              <el-tag size="small" effect="plain">{{ railIdentityText(group) }}</el-tag>
            </span>
            <span class="group-list__meta">{{ group.roleCount }} 个角色 · {{ group.memberCount }} 名协作者</span>
            <span class="group-list__creator" :title="`创建者：${group.creatorName || '未知用户'}`">
              创建者：{{ group.creatorName || '未知用户' }}
            </span>
          </button>
        </div>
      </aside>

      <section v-if="selectedGroup || canManageGlobalTokens" class="group-detail">
        <header v-if="selectedGroup" class="group-detail__header">
          <div>
            <div class="group-detail__title-row">
              <h2>{{ selectedGroup.groupName }}</h2>
              <el-tag effect="plain">{{ accessIdentityText(selectedGroup) }}</el-tag>
            </div>
            <p>{{ selectedGroup.remark || '未填写备注' }}</p>
            <div class="group-detail__ownership">
              <span>创建者：{{ selectedGroup.creatorName || '未知用户' }}</span>
              <span>所有者：{{ ownerNamesText(selectedGroup) }}</span>
            </div>
          </div>
          <div v-if="canManageGroupSettings" class="group-detail__actions">
            <el-button :icon="Edit" @click="openEditGroup">设置</el-button>
            <el-button type="danger" plain :icon="Delete" @click="handleDeleteGroup">删除</el-button>
          </div>
        </header>
        <header v-else class="group-detail__header">
          <div class="group-detail__title-row">
            <h2>全局供给令牌</h2>
            <el-tag effect="plain">平台管理员</el-tag>
          </div>
        </header>

        <el-tabs v-model="activeTab" class="group-tabs">
          <el-tab-pane v-if="selectedGroup" label="角色与成员" name="roles">
            <div class="section-toolbar">
              <strong>角色成员关系</strong>
              <el-button v-if="canManageGroupContent" type="primary" :icon="Plus" @click="openCreateRole">创建组角色</el-button>
            </div>
            <PersistentTableScrollFrame>
              <el-table :data="rolesQuery.data.value || []" v-loading="rolesQuery.isLoading.value" border>
                <el-table-column prop="roleName" label="角色名称" min-width="170" />
                <el-table-column prop="roleCode" label="角色编码" min-width="180" />
                <el-table-column label="作用域" width="120">
                  <template #default="{ row }"><el-tag effect="plain">{{ scopeText(row.roleScope) }}</el-tag></template>
                </el-table-column>
                <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
                <el-table-column label="操作" width="230" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" :icon="UserFilled" @click="openRoleMembers(row)">成员</el-button>
                    <el-button v-if="canManageGroupContent && row.editable" link type="primary" :icon="Edit" @click="openEditRole(row)">编辑</el-button>
                    <el-button v-if="canManageGroupContent && row.editable" link type="danger" :icon="Delete" @click="handleDeleteRole(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </PersistentTableScrollFrame>
          </el-tab-pane>

          <el-tab-pane v-if="selectedGroup" label="协作成员" name="collaborators">
            <div class="section-toolbar">
              <strong>协作成员</strong>
              <el-button v-if="canManageGroupSettings" type="primary" :icon="Plus" @click="openCollaboratorDialog">添加成员</el-button>
            </div>
            <el-table :data="collaboratorsQuery.data.value || []" v-loading="collaboratorsQuery.isLoading.value" border>
              <el-table-column prop="realName" label="姓名" min-width="220" />
              <el-table-column label="组内身份" width="140">
                <template #default="{ row }"><el-tag effect="plain">{{ memberRoleText(row.memberRole) }}</el-tag></template>
              </el-table-column>
              <el-table-column v-if="canManageGroupSettings" label="操作" width="120">
                <template #default="{ row }">
                  <el-button link type="danger" @click="removeCollaborator(row.userId, row.realName)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane v-if="selectedGroup && canManageGroupSettings" label="供给令牌" name="tokens">
            <div class="section-toolbar">
              <strong>组供给令牌</strong>
              <el-button type="primary" :icon="Key" @click="openTokenDialog('GROUP')">创建令牌</el-button>
            </div>
            <el-table :data="groupTokensQuery.data.value?.items || []" v-loading="groupTokensQuery.isLoading.value" border>
              <el-table-column prop="name" label="名称" min-width="180" />
              <el-table-column prop="description" label="备注" min-width="200" show-overflow-tooltip />
              <el-table-column label="状态" width="100"><template #default="{ row }">{{ tokenStatusText(row.status) }}</template></el-table-column>
              <el-table-column prop="expiresAt" label="有效期" min-width="180"><template #default="{ row }">{{ row.expiresAt || '无限制' }}</template></el-table-column>
              <el-table-column label="操作" width="280" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="revealToken(row, 'GROUP')">查看</el-button>
                  <el-button link type="primary" :icon="RefreshRight" @click="rotateToken(row, 'GROUP')">轮换</el-button>
                  <el-button v-if="row.status === 'ACTIVE'" link type="warning" @click="revokeToken(row, 'GROUP')">撤销</el-button>
                  <el-button link type="danger" @click="removeToken(row, 'GROUP')">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane v-if="canManageGlobalTokens" label="全局令牌" name="global-tokens">
            <div class="section-toolbar">
              <strong>全局供给令牌</strong>
              <el-button type="primary" :icon="Key" @click="openTokenDialog('GLOBAL')">创建令牌</el-button>
            </div>
            <el-table :data="globalTokensQuery.data.value?.items || []" v-loading="globalTokensQuery.isLoading.value" border>
              <el-table-column prop="name" label="名称" min-width="180" />
              <el-table-column prop="description" label="备注" min-width="200" show-overflow-tooltip />
              <el-table-column label="状态" width="100"><template #default="{ row }">{{ tokenStatusText(row.status) }}</template></el-table-column>
              <el-table-column prop="expiresAt" label="有效期" min-width="180"><template #default="{ row }">{{ row.expiresAt || '无限制' }}</template></el-table-column>
              <el-table-column label="操作" width="280" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="revealToken(row, 'GLOBAL')">查看</el-button>
                  <el-button link type="primary" :icon="RefreshRight" @click="rotateToken(row, 'GLOBAL')">轮换</el-button>
                  <el-button v-if="row.status === 'ACTIVE'" link type="warning" @click="revokeToken(row, 'GLOBAL')">撤销</el-button>
                  <el-button link type="danger" @click="removeToken(row, 'GLOBAL')">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </section>

      <el-empty v-else class="group-detail group-detail--empty" description="选择或创建一个角色组" />
    </div>

    <el-dialog v-model="groupDialogVisible" :title="groupDialogMode === 'create' ? '创建角色组' : '角色组设置'" width="520px">
      <el-form label-position="top">
        <el-form-item label="名称" required><el-input v-model="groupForm.groupName" maxlength="128" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="groupForm.remark" type="textarea" :rows="3" maxlength="256" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="groupDialogVisible = false">取消</el-button><el-button type="primary" :loading="saveGroupMutation.isPending.value" :disabled="!groupForm.groupName.trim()" @click="saveGroupMutation.mutate()">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" :title="roleDialogMode === 'create' ? '创建组角色' : '编辑组角色'" width="520px">
      <el-form label-position="top">
        <el-form-item label="角色编码" required><el-input v-model="roleForm.roleCode" :disabled="roleDialogMode === 'edit'" maxlength="64" /></el-form-item>
        <el-form-item label="角色名称" required><el-input v-model="roleForm.roleName" maxlength="64" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="roleForm.remark" type="textarea" :rows="3" maxlength="256" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="roleDialogVisible = false">取消</el-button><el-button type="primary" :loading="saveRoleMutation.isPending.value" :disabled="!roleForm.roleCode.trim() || !roleForm.roleName.trim()" @click="saveRoleMutation.mutate()">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="collaboratorDialogVisible" title="添加协作成员" width="520px">
      <el-form label-position="top">
        <el-form-item label="姓名" required>
          <el-select v-model="collaboratorForm.userId" filterable remote :remote-method="searchPublicUsers" :loading="publicUserLoading" placeholder="搜索公开姓名" style="width: 100%">
            <el-option v-for="user in publicUserOptions" :key="user.id" :label="user.realName" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="组内身份"><el-segmented v-model="collaboratorForm.memberRole" :options="[{ label: '协管员', value: 'MANAGER' }, { label: '所有者', value: 'OWNER' }]" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="collaboratorDialogVisible = false">取消</el-button><el-button type="primary" :loading="saveCollaboratorMutation.isPending.value" :disabled="!collaboratorForm.userId" @click="saveCollaboratorMutation.mutate()">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="roleMembersVisible" :title="`${memberRole?.roleName || ''} · 成员`" width="720px">
      <div v-if="canAssignRoleMembers" class="member-picker">
        <el-select v-model="selectedMemberUserIds" multiple filterable remote :remote-method="searchPublicUsers" :loading="publicUserLoading" placeholder="搜索公开姓名" style="width: 100%">
          <el-option v-for="user in publicUserOptions" :key="user.id" :label="user.realName" :value="user.id" />
        </el-select>
        <el-button type="primary" :icon="Check" :disabled="!selectedMemberUserIds.length" @click="applyRoleMembers">批量添加</el-button>
      </div>
      <el-table :data="roleMembers" v-loading="roleMembersLoading" border max-height="420">
        <el-table-column prop="realName" label="姓名" />
        <el-table-column v-if="canAssignRoleMembers" label="操作" width="100"><template #default="{ row }"><el-button link type="danger" @click="deleteRoleMember(row)">移除</el-button></template></el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="tokenDialogVisible" :title="tokenSubject === 'GLOBAL' ? '创建全局令牌' : '创建组供给令牌'" width="520px">
      <el-form label-position="top">
        <el-form-item label="名称" required><el-input v-model="tokenForm.name" maxlength="64" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="tokenForm.description" maxlength="255" /></el-form-item>
        <el-form-item label="有效期"><el-date-picker v-model="tokenForm.expiresAt" type="datetime" placeholder="无限制" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="tokenDialogVisible = false">取消</el-button><el-button type="primary" :loading="createTokenMutation.isPending.value" :disabled="!tokenForm.name.trim()" @click="createTokenMutation.mutate()">创建</el-button></template>
    </el-dialog>

    <el-dialog
      v-model="secretDialogVisible"
      title="访问令牌"
      width="620px"
      append-to-body
      @closed="currentSecret = ''"
    >
      <el-input :model-value="currentSecret" readonly type="textarea" :rows="3" class="secret-value" />
      <template #footer><el-button type="primary" :icon="CopyDocument" @click="copySecret">复制令牌</el-button></template>
    </el-dialog>
  </PageContainer>
</template>

<style scoped lang="scss">
.role-group-workspace { display: grid; grid-template-columns: 280px minmax(0, 1fr); min-height: 680px; border: 1px solid var(--idm-border-color-light); background: rgba(255, 255, 255, 0.78); backdrop-filter: blur(18px); box-shadow: var(--idm-shadow-card); }
.group-rail { padding: 18px 14px; border-right: 1px solid var(--idm-border-color-light); background: rgba(248, 250, 249, 0.82); }
.group-rail__header, .section-toolbar, .group-detail__header, .group-detail__title-row, .member-picker { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.group-rail__header { margin-bottom: 16px; }
.group-rail__header > div { display: flex; align-items: baseline; gap: 8px; }
.group-rail__header span, .section-toolbar span { color: var(--idm-text-secondary); font-size: 13px; }
.group-list { display: flex; flex-direction: column; gap: 6px; }
.group-list__item { display: grid; gap: 6px; width: 100%; padding: 13px 12px; border: 1px solid transparent; background: transparent; color: var(--idm-text-primary); text-align: left; cursor: pointer; transition: background 180ms ease, border-color 180ms ease, transform 180ms ease; }
.group-list__item:hover { background: #fff; border-color: var(--idm-border-color-light); transform: translateX(2px); }
.group-list__item.is-active { background: #fff; border-color: #b8c6bd; box-shadow: 0 8px 24px rgba(31, 48, 38, 0.08); }
.group-list__headline { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-width: 0; }
.group-list__headline .el-tag { flex: 0 0 auto; }
.group-list__name { overflow: hidden; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.group-list__meta { color: var(--idm-text-secondary); font-size: 12px; }
.group-list__creator { overflow: hidden; color: var(--idm-text-secondary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.group-detail { min-width: 0; padding: 26px 28px; }
.group-detail--empty { display: flex; align-items: center; justify-content: center; }
.group-detail__header { align-items: flex-start; padding-bottom: 18px; border-bottom: 1px solid var(--idm-border-color-lighter); }
.group-detail__title-row { justify-content: flex-start; }
.group-detail h2 { margin: 0; font-size: 22px; letter-spacing: 0; }
.group-detail p { margin: 8px 0 0; color: var(--idm-text-secondary); }
.group-detail__ownership { display: flex; flex-wrap: wrap; gap: 8px 20px; margin-top: 10px; color: var(--idm-text-secondary); font-size: 13px; }
.group-tabs { margin-top: 10px; }
.section-toolbar { margin: 12px 0 16px; }
.section-toolbar > div { display: flex; flex-direction: column; gap: 4px; }
.member-picker { margin-bottom: 16px; }
.secret-value :deep(textarea) { font-family: Consolas, monospace; line-height: 1.6; word-break: break-all; }
</style>
