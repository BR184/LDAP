<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, Check, CopyDocument, Delete, Edit, Plus, RefreshRight, UserFilled } from '@element-plus/icons-vue'
import PersistentTableScrollFrame from '@/components/table-scroll/PersistentTableScrollFrame.vue'
import { verifyMyPassword } from '@/api/modules/user'
import {
  addRoleMembers,
  createGlobalSubscription,
  createGroupSubscription,
  createRoleGroup,
  createRoleGroupRole,
  deleteGlobalSubscription,
  deleteGroupSubscription,
  deleteRoleGroup,
  deleteRoleGroupRole,
  disableGlobalSubscription,
  disableGroupSubscription,
  enableGlobalSubscription,
  enableGroupSubscription,
  fetchGlobalSubscriptions,
  fetchGroupSubscriptions,
  fetchRoleGroupCollaborators,
  fetchRoleGroupRoles,
  fetchRoleGroups,
  fetchRoleMembers,
  removeRoleGroupCollaborator,
  removeRoleMember,
  revealGlobalSubscription,
  revealGroupSubscription,
  rotateGlobalSubscription,
  rotateGroupSubscription,
  saveRoleGroupCollaborator,
  searchRoleGroupUsers,
  updateRoleGroup,
  updateRoleGroupRole,
} from '@/api/modules/role-group'
import { fetchRoles } from '@/api/modules/role'
import { useAuthStore } from '@/stores/auth'
import type {
  PublicUserItem,
  RoleGroupItem,
  RoleGroupMemberRole,
  RoleGroupRoleItem,
  SubscriptionCredential,
  SubscriptionItem,
} from '@/types/role-group'

const queryClient = useQueryClient()
const authStore = useAuthStore()
const selectedGroupId = ref<number | null>(null)
const activeTab = ref('roles')
type RoleGroupPageView = 'groups' | 'global-subscriptions'
const activeView = ref<RoleGroupPageView>('groups')
const pageViewOptions: Array<{ label: string; value: RoleGroupPageView }> = [
  { label: '角色组', value: 'groups' },
  { label: '全局订阅', value: 'global-subscriptions' },
]
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
const canManageGlobalSubscriptions = computed(() => authStore.isAdmin && hasRoleGroupManagement.value)
const showGlobalSubscriptionView = computed(
  () => canManageGlobalSubscriptions.value && activeView.value === 'global-subscriptions',
)

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
const groupSubscriptionsQuery = useQuery({
  queryKey: computed(() => ['role-groups', viewerId.value, selectedGroupId.value, 'subscriptions']),
  queryFn: () => fetchGroupSubscriptions(selectedGroupId.value!),
  enabled: computed(() => hasValidGroupSelection.value && canManageGroupSettings.value),
})
const globalSubscriptionsQuery = useQuery({
  queryKey: computed(() => ['role-supply-subscriptions', viewerId.value, 'global']),
  queryFn: fetchGlobalSubscriptions,
  enabled: computed(() => viewerId.value !== null && canManageGlobalSubscriptions.value),
})

watch(
  viewerId,
  () => {
    selectedGroupId.value = null
    activeTab.value = 'roles'
    activeView.value = 'groups'
  },
  { immediate: true },
)

watch(
  groups,
  (items) => {
    if (!items.length) {
      selectedGroupId.value = null
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

const subscriptionDialogVisible = ref(false)
const subscriptionSubject = ref<'GROUP' | 'GLOBAL'>('GROUP')
const subscriptionForm = reactive<{ name: string; description: string; roleIds: number[] }>({
  name: '',
  description: '',
  roleIds: [],
})
const credentialDialogVisible = ref(false)
const currentCredential = ref<SubscriptionCredential | null>(null)

const allRolesQuery = useQuery({
  queryKey: computed(() => ['role-subscription-options', viewerId.value]),
  queryFn: fetchRoles,
  enabled: computed(() =>
    viewerId.value !== null
    && canManageGlobalSubscriptions.value
    && subscriptionDialogVisible.value
    && subscriptionSubject.value === 'GLOBAL'),
})

const subscriptionRoleOptions = computed(() =>
  subscriptionSubject.value === 'GLOBAL' ? allRolesQuery.data.value || [] : rolesQuery.data.value || [])

const credentialRows = computed<Array<{ label: string; value: string }>>(() => {
  const credential = currentCredential.value
  if (!credential) return []
  const { mq } = credential
  return [
    { label: 'MQ 主机', value: mq.host },
    { label: 'MQ 端口', value: String(mq.port) },
    { label: 'vhost', value: mq.vhost },
    { label: '队列名', value: mq.queue },
    { label: '账号', value: mq.username },
    { label: '密码', value: mq.password },
  ]
})

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
const refreshGroupSubscriptions = () =>
  queryClient.invalidateQueries({
    queryKey: ['role-groups', viewerId.value, selectedGroupId.value, 'subscriptions'],
    exact: true,
  })
const refreshGlobalSubscriptions = () => queryClient.invalidateQueries({
  queryKey: ['role-supply-subscriptions', viewerId.value, 'global'],
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

const createSubscriptionMutation = useMutation({
  mutationFn: () => {
    const name = subscriptionForm.name
    const description = subscriptionForm.description || null
    // 组订阅为整组动态范围，不再提交角色选集；全局订阅保留显式选角。
    return subscriptionSubject.value === 'GLOBAL'
      ? createGlobalSubscription({ name, description, roleIds: subscriptionForm.roleIds })
      : createGroupSubscription(selectedGroupId.value!, { name, description })
  },
  onSuccess: async (created) => {
    subscriptionDialogVisible.value = false
    showCredential(created.credential)
    await (subscriptionSubject.value === 'GLOBAL' ? refreshGlobalSubscriptions() : refreshGroupSubscriptions())
    ElMessage.success('订阅已创建')
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

function openSubscriptionDialog(subject: 'GROUP' | 'GLOBAL') {
  subscriptionSubject.value = subject
  Object.assign(subscriptionForm, { name: '', description: '', roleIds: [] })
  subscriptionDialogVisible.value = true
}

function selectAllSubscriptionRoles() {
  subscriptionForm.roleIds = subscriptionRoleOptions.value.map((role) => role.id)
}

async function rotateSubscription(subscription: SubscriptionItem, subject: 'GROUP' | 'GLOBAL') {
  await ElMessageBox.confirm('轮换后将生成新的订阅令牌与 MQ 密码，旧凭证立即失效，是否继续？', '轮换凭证', {
    type: 'warning',
    confirmButtonText: '轮换',
    cancelButtonText: '取消',
  })
  const credential = subject === 'GLOBAL'
    ? await rotateGlobalSubscription(subscription.id)
    : await rotateGroupSubscription(selectedGroupId.value!, subscription.id)
  showCredential(credential)
  await (subject === 'GLOBAL' ? refreshGlobalSubscriptions() : refreshGroupSubscriptions())
  ElMessage.success('凭证已轮换')
}

async function revealSubscription(subscription: SubscriptionItem, subject: 'GROUP' | 'GLOBAL') {
  const prompt = await ElMessageBox.prompt('请输入当前登录密码', '查看访问凭证', {
    inputType: 'password',
    inputPattern: /\S+/,
    inputErrorMessage: '请输入当前登录密码',
    confirmButtonText: '验证并查看',
    cancelButtonText: '取消',
  })
  const verification = await verifyMyPassword({ oldPassword: prompt.value })
  const credential = subject === 'GLOBAL'
    ? await revealGlobalSubscription(subscription.id, verification.verificationToken)
    : await revealGroupSubscription(selectedGroupId.value!, subscription.id, verification.verificationToken)
  showCredential(credential)
}

async function toggleSubscription(subscription: SubscriptionItem, subject: 'GROUP' | 'GLOBAL') {
  const disabling = subscription.status === 'ENABLED'
  await ElMessageBox.confirm(
    disabling
      ? '停用后第三方将暂停接收新消息（消息仍在队列中积压不丢失），是否继续？'
      : '启用后第三方可继续接收积压的消息，是否继续？',
    disabling ? '停用订阅' : '启用订阅',
    {
      type: 'warning',
      confirmButtonText: disabling ? '停用' : '启用',
      cancelButtonText: '取消',
    },
  )
  if (disabling) {
    if (subject === 'GLOBAL') await disableGlobalSubscription(subscription.id)
    else await disableGroupSubscription(selectedGroupId.value!, subscription.id)
  } else if (subject === 'GLOBAL') {
    await enableGlobalSubscription(subscription.id)
  } else {
    await enableGroupSubscription(selectedGroupId.value!, subscription.id)
  }
  await (subject === 'GLOBAL' ? refreshGlobalSubscriptions() : refreshGroupSubscriptions())
  ElMessage.success(disabling ? '订阅已停用' : '订阅已启用')
}

async function removeSubscription(subscription: SubscriptionItem, subject: 'GROUP' | 'GLOBAL') {
  await ElMessageBox.confirm(
    `确认删除订阅“${subscription.name}”吗？删除后专属队列、MQ 账号与订阅令牌将一并清理，队列中未消费的消息将丢失。`,
    '删除订阅',
    {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    },
  )
  if (subject === 'GLOBAL') await deleteGlobalSubscription(subscription.id)
  else await deleteGroupSubscription(selectedGroupId.value!, subscription.id)
  await (subject === 'GLOBAL' ? refreshGlobalSubscriptions() : refreshGroupSubscriptions())
  ElMessage.success('订阅已删除')
}

function showCredential(credential: SubscriptionCredential) {
  currentCredential.value = credential
  credentialDialogVisible.value = true
}

async function copyText(text: string, label: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
  } else {
    const input = document.createElement('textarea')
    input.value = text
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    input.remove()
  }
  ElMessage.success(`${label}已复制`)
}

function copyFullCredential() {
  const credential = currentCredential.value
  if (!credential) return
  const lines = [
    `订阅令牌=${credential.tokenSecret}`,
    `MQ主机=${credential.mq.host}`,
    `MQ端口=${credential.mq.port}`,
    `vhost=${credential.mq.vhost}`,
    `队列名=${credential.mq.queue}`,
    `账号=${credential.mq.username}`,
    `密码=${credential.mq.password}`,
  ]
  void copyText(lines.join('\n'), '完整凭证')
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

function subscriptionStatusText(status: string) {
  return status === 'ENABLED' ? '已启用' : '已停用'
}

function formatDateTime(value?: string | null) {
  return value ? value.replace('T', ' ') : '--'
}
</script>

<template>
  <PageContainer title="角色组管理">
    <template #extra>
      <el-segmented
        v-if="canManageGlobalSubscriptions"
        v-model="activeView"
        :options="pageViewOptions"
        class="view-switch"
      />
    </template>

    <section v-if="showGlobalSubscriptionView" class="group-detail group-detail--panel">
      <header class="group-detail__header">
        <div>
          <div class="group-detail__title-row">
            <h2>全局订阅推送</h2>
            <el-tag effect="plain">平台管理员</el-tag>
          </div>
          <p>平台级订阅：可订阅任意角色，面向跨角色组场景的第三方系统，与具体角色组无关</p>
        </div>
      </header>
      <div class="section-toolbar">
        <strong>订阅列表</strong>
        <el-button type="primary" :icon="Bell" @click="openSubscriptionDialog('GLOBAL')">创建订阅令牌</el-button>
      </div>
      <el-table :data="globalSubscriptionsQuery.data.value || []" v-loading="globalSubscriptionsQuery.isLoading.value" border>
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="订阅角色数" width="110" align="center">
          <template #default="{ row }">{{ row.roleCount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" effect="plain">{{ subscriptionStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.gmtCreate) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="revealSubscription(row, 'GLOBAL')">查看凭证</el-button>
            <el-button link type="primary" :icon="RefreshRight" @click="rotateSubscription(row, 'GLOBAL')">轮换</el-button>
            <el-button link type="warning" @click="toggleSubscription(row, 'GLOBAL')">{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
            <el-button link type="danger" @click="removeSubscription(row, 'GLOBAL')">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <div v-else class="role-group-workspace">
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

      <section v-if="selectedGroup" class="group-detail">
        <header class="group-detail__header">
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

          <el-tab-pane v-if="selectedGroup && canManageGroupSettings" label="订阅推送" name="subscriptions">
            <div class="section-toolbar">
              <strong>组订阅推送</strong>
              <el-button type="primary" :icon="Bell" @click="openSubscriptionDialog('GROUP')">创建订阅令牌</el-button>
            </div>
            <el-table :data="groupSubscriptionsQuery.data.value || []" v-loading="groupSubscriptionsQuery.isLoading.value" border>
              <el-table-column prop="name" label="名称" min-width="160" />
              <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
              <el-table-column label="订阅角色数" width="110" align="center">
                <template #default="{ row }">{{ row.roleCount }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" effect="plain">{{ subscriptionStatusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" min-width="165">
                <template #default="{ row }">{{ formatDateTime(row.gmtCreate) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="320" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="revealSubscription(row, 'GROUP')">查看凭证</el-button>
                  <el-button link type="primary" :icon="RefreshRight" @click="rotateSubscription(row, 'GROUP')">轮换</el-button>
                  <el-button link type="warning" @click="toggleSubscription(row, 'GROUP')">{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
                  <el-button link type="danger" @click="removeSubscription(row, 'GROUP')">删除</el-button>
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

    <el-dialog v-model="subscriptionDialogVisible" :title="subscriptionSubject === 'GLOBAL' ? '创建全局订阅令牌' : '创建组订阅令牌'" width="560px">
      <el-form label-position="top">
        <el-form-item label="名称" required><el-input v-model="subscriptionForm.name" maxlength="64" placeholder="对接方名称，如：制品平台" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="subscriptionForm.description" maxlength="255" /></el-form-item>
        <el-form-item v-if="subscriptionSubject === 'GROUP'" label="订阅范围">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="订阅整个角色组"
            description="当前及后续新增的组内角色自动纳入推送范围，无需重新勾选角色或重签令牌；组外与全局角色不会被推送。"
          />
        </el-form-item>
        <el-form-item v-else label="订阅角色" required>
          <div class="subscription-role-picker">
            <el-select v-model="subscriptionForm.roleIds" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择需要推送的角色" style="width: 100%">
              <el-option v-for="role in subscriptionRoleOptions" :key="role.id" :label="`${role.roleName}（${role.roleCode}）`" :value="role.id" />
            </el-select>
            <el-button :disabled="!subscriptionRoleOptions.length" @click="selectAllSubscriptionRoles">全选</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="subscriptionDialogVisible = false">取消</el-button><el-button type="primary" :loading="createSubscriptionMutation.isPending.value" :disabled="!subscriptionForm.name.trim() || (subscriptionSubject === 'GLOBAL' && !subscriptionForm.roleIds.length)" @click="createSubscriptionMutation.mutate()">创建</el-button></template>
    </el-dialog>

    <el-dialog
      v-model="credentialDialogVisible"
      title="访问凭证"
      width="680px"
      append-to-body
      @closed="currentCredential = null"
    >
      <template v-if="currentCredential">
        <el-alert type="warning" :closable="false" show-icon title="凭证仅在创建与轮换时一次性展示，请立即妥善保存；关闭后需验证登录密码才能重看。" />
        <div class="credential-block">
          <div class="credential-block__head">
            <strong>接入令牌</strong>
            <el-button link type="primary" :icon="CopyDocument" @click="copyText(currentCredential.tokenSecret, '接入令牌')">复制接入令牌</el-button>
          </div>
          <el-input :model-value="currentCredential.tokenSecret" readonly type="textarea" :rows="2" class="secret-value" />
          <p class="credential-hint">
            对接平台只需粘贴此令牌即可自动完成接入：平台后端会凭令牌获取专属队列连接信息与角色目录，
            无需人工抄录 MQ 参数。令牌同时用于 /api/v2/open/role-supply/context、/snapshot 与 /changes。
          </p>
        </div>
        <el-collapse class="credential-collapse">
          <el-collapse-item name="mq">
            <template #title>RabbitMQ 连接参数（手工接入或排障时查看）</template>
            <div v-for="item in credentialRows" :key="item.label" class="credential-row">
              <span class="credential-row__label">{{ item.label }}</span>
              <span class="credential-row__value">{{ item.value }}</span>
              <el-button link type="primary" :icon="CopyDocument" @click="copyText(item.value, item.label)">复制</el-button>
            </div>
          </el-collapse-item>
        </el-collapse>
      </template>
      <template #footer>
        <el-button type="primary" :icon="CopyDocument" :disabled="!currentCredential" @click="copyText(currentCredential?.tokenSecret ?? '', '接入令牌')">复制接入令牌</el-button>
        <el-button :icon="CopyDocument" :disabled="!currentCredential" @click="copyFullCredential">复制全部凭证</el-button>
      </template>
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
.group-detail--panel { border: 1px solid var(--idm-border-color-light); background: rgba(255, 255, 255, 0.78); backdrop-filter: blur(18px); box-shadow: var(--idm-shadow-card); }
.group-detail__header { align-items: flex-start; padding-bottom: 18px; border-bottom: 1px solid var(--idm-border-color-lighter); }
.group-detail__title-row { justify-content: flex-start; }
.group-detail h2 { margin: 0; font-size: 22px; letter-spacing: 0; }
.group-detail p { margin: 8px 0 0; color: var(--idm-text-secondary); }
.group-detail__ownership { display: flex; flex-wrap: wrap; gap: 8px 20px; margin-top: 10px; color: var(--idm-text-secondary); font-size: 13px; }
.group-tabs { margin-top: 10px; }
.view-switch { --el-segmented-item-selected-color: #155e57; --el-segmented-item-selected-bg-color: #e5f1ee; }
.section-toolbar { margin: 12px 0 16px; }
.section-toolbar > div { display: flex; flex-direction: column; gap: 4px; }
.member-picker { margin-bottom: 16px; }
.subscription-role-picker { display: flex; gap: 10px; width: 100%; }
.credential-block { margin-top: 14px; }
.credential-block__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.credential-hint { margin: 6px 0 0; color: var(--idm-text-secondary); font-size: 12px; }
.credential-row { display: grid; grid-template-columns: 90px minmax(0, 1fr) auto; align-items: center; gap: 10px; padding: 6px 0; border-bottom: 1px dashed var(--idm-border-color-lighter); }
.credential-row__label { color: var(--idm-text-secondary); font-size: 13px; }
.credential-row__value { overflow-wrap: anywhere; font-family: Consolas, monospace; font-size: 13px; }
.secret-value :deep(textarea) { font-family: Consolas, monospace; line-height: 1.6; word-break: break-all; }
</style>
