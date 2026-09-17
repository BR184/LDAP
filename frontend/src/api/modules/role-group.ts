import request from '@/api/request'
import type {
  CreatedSubscription,
  GovernedRoleItem,
  PersonalRoleContext,
  PublicUserItem,
  RoleGroupItem,
  RoleGroupMemberItem,
  RoleGroupMemberRole,
  RoleGroupRoleItem,
  RoleScope,
  SubscriptionCredential,
  SubscriptionItem,
} from '@/types/role-group'

export const fetchRoleGroups = () => request.get<never, RoleGroupItem[]>('/v2/role-groups')
export const fetchRoleGroup = (groupId: number) => request.get<never, RoleGroupItem>(`/v2/role-groups/${groupId}`)
export const createRoleGroup = (payload: { groupName: string; remark?: string | null }) =>
  request.post<never, RoleGroupItem>('/v2/role-groups', payload)
export const updateRoleGroup = (groupId: number, payload: { groupName: string; remark?: string | null }) =>
  request.put<never, RoleGroupItem>(`/v2/role-groups/${groupId}`, payload)
export const deleteRoleGroup = (groupId: number) => request.delete<never, void>(`/v2/role-groups/${groupId}`)

export const fetchRoleGroupCollaborators = (groupId: number) =>
  request.get<never, RoleGroupMemberItem[]>(`/v2/role-groups/${groupId}/collaborators`)
export const saveRoleGroupCollaborator = (groupId: number, userId: number, memberRole: RoleGroupMemberRole) =>
  request.put<never, RoleGroupMemberItem>(`/v2/role-groups/${groupId}/collaborators/${userId}`, { memberRole })
export const removeRoleGroupCollaborator = (groupId: number, userId: number) =>
  request.delete<never, void>(`/v2/role-groups/${groupId}/collaborators/${userId}`)
export const searchRoleGroupUsers = (groupId: number, keyword: string) =>
  request.get<never, PublicUserItem[]>(`/v2/role-groups/${groupId}/users`, { params: { keyword } })

export const fetchRoleGroupRoles = (groupId: number) =>
  request.get<never, RoleGroupRoleItem[]>(`/v2/role-groups/${groupId}/roles`)
export const createRoleGroupRole = (
  groupId: number,
  payload: { roleCode: string; roleName: string; remark?: string | null },
) => request.post<never, RoleGroupRoleItem>(`/v2/role-groups/${groupId}/roles`, payload)
export const updateRoleGroupRole = (
  groupId: number,
  roleId: number,
  payload: { roleName: string; remark?: string | null },
) => request.put<never, RoleGroupRoleItem>(`/v2/role-groups/${groupId}/roles/${roleId}`, payload)
export const deleteRoleGroupRole = (groupId: number, roleId: number) =>
  request.delete<never, void>(`/v2/role-groups/${groupId}/roles/${roleId}`)
export const fetchRoleMembers = (groupId: number, roleId: number) =>
  request.get<never, PublicUserItem[]>(`/v2/role-groups/${groupId}/roles/${roleId}/members`)
export const addRoleMembers = (groupId: number, roleId: number, userIds: number[]) =>
  request.post<never, void>(`/v2/role-groups/${groupId}/roles/${roleId}/members`, { userIds })
export const removeRoleMember = (groupId: number, roleId: number, userId: number) =>
  request.delete<never, void>(`/v2/role-groups/${groupId}/roles/${roleId}/members/${userId}`)

export const fetchGroupSubscriptions = (groupId: number) =>
  request.get<never, SubscriptionItem[]>(`/v2/role-groups/${groupId}/subscriptions`)
export const createGroupSubscription = (
  groupId: number,
  payload: { name: string; description?: string | null; roleIds: number[] },
) => request.post<never, CreatedSubscription>(`/v2/role-groups/${groupId}/subscriptions`, payload)
export const rotateGroupSubscription = (groupId: number, subscriptionId: number) =>
  request.post<never, SubscriptionCredential>(
    `/v2/role-groups/${groupId}/subscriptions/${subscriptionId}/rotations`,
  )
export const revealGroupSubscription = (groupId: number, subscriptionId: number, verificationToken: string) =>
  request.post<never, SubscriptionCredential>(
    `/v2/role-groups/${groupId}/subscriptions/${subscriptionId}/secret-reveals`,
    { verificationToken },
  )
export const disableGroupSubscription = (groupId: number, subscriptionId: number) =>
  request.post<never, void>(`/v2/role-groups/${groupId}/subscriptions/${subscriptionId}/disables`)
export const enableGroupSubscription = (groupId: number, subscriptionId: number) =>
  request.post<never, void>(`/v2/role-groups/${groupId}/subscriptions/${subscriptionId}/enables`)
export const deleteGroupSubscription = (groupId: number, subscriptionId: number) =>
  request.delete<never, void>(`/v2/role-groups/${groupId}/subscriptions/${subscriptionId}`)

export const fetchGlobalSubscriptions = () =>
  request.get<never, SubscriptionItem[]>('/v2/role-supply-subscriptions/global')
export const createGlobalSubscription = (payload: { name: string; description?: string | null; roleIds: number[] }) =>
  request.post<never, CreatedSubscription>('/v2/role-supply-subscriptions/global', payload)
export const rotateGlobalSubscription = (subscriptionId: number) =>
  request.post<never, SubscriptionCredential>(`/v2/role-supply-subscriptions/global/${subscriptionId}/rotations`)
export const revealGlobalSubscription = (subscriptionId: number, verificationToken: string) =>
  request.post<never, SubscriptionCredential>(`/v2/role-supply-subscriptions/global/${subscriptionId}/secret-reveals`, {
    verificationToken,
  })
export const disableGlobalSubscription = (subscriptionId: number) =>
  request.post<never, void>(`/v2/role-supply-subscriptions/global/${subscriptionId}/disables`)
export const enableGlobalSubscription = (subscriptionId: number) =>
  request.post<never, void>(`/v2/role-supply-subscriptions/global/${subscriptionId}/enables`)
export const deleteGlobalSubscription = (subscriptionId: number) =>
  request.delete<never, void>(`/v2/role-supply-subscriptions/global/${subscriptionId}`)

export const fetchPersonalRoleContext = () =>
  request.get<never, PersonalRoleContext>('/v2/users/me/role-context')

export const fetchGovernedRoles = () => request.get<never, GovernedRoleItem[]>('/v2/role-governance/roles')
export const updateGovernedRoleScope = (roleId: number, roleScope: RoleScope, roleGroupId: number | null = null) =>
  request.put<never, GovernedRoleItem>(`/v2/role-governance/roles/${roleId}/scope`, { roleScope, roleGroupId })
