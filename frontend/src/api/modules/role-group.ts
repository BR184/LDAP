import request from '@/api/request'
import type {
  CreatedRoleSupplyToken,
  GovernedRoleItem,
  PersonalRoleContext,
  PublicUserItem,
  RoleGroupItem,
  RoleGroupMemberItem,
  RoleGroupMemberRole,
  RoleGroupRoleItem,
  RoleScope,
  RoleSupplyTokenPage,
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

export const fetchGroupTokens = (groupId: number) =>
  request.get<never, RoleSupplyTokenPage>(`/v2/role-groups/${groupId}/tokens`)
export const createGroupToken = (
  groupId: number,
  payload: { name: string; description?: string | null; expiresAt?: string | null },
) => request.post<never, CreatedRoleSupplyToken>(`/v2/role-groups/${groupId}/tokens`, payload)
export const rotateGroupToken = (groupId: number, tokenId: number) =>
  request.post<never, CreatedRoleSupplyToken>(`/v2/role-groups/${groupId}/tokens/${tokenId}/rotations`)
export const revealGroupToken = (groupId: number, tokenId: number, verificationToken: string) =>
  request.post<never, { secret: string }>(`/v2/role-groups/${groupId}/tokens/${tokenId}/secret-reveals`, {
    verificationToken,
  })
export const revokeGroupToken = (groupId: number, tokenId: number) =>
  request.post<never, void>(`/v2/role-groups/${groupId}/tokens/${tokenId}/revocations`)
export const deleteGroupToken = (groupId: number, tokenId: number) =>
  request.delete<never, void>(`/v2/role-groups/${groupId}/tokens/${tokenId}`)

export const fetchGlobalTokens = () => request.get<never, RoleSupplyTokenPage>('/v2/role-supply-tokens/global')
export const createGlobalToken = (payload: { name: string; description?: string | null; expiresAt?: string | null }) =>
  request.post<never, CreatedRoleSupplyToken>('/v2/role-supply-tokens/global', payload)
export const rotateGlobalToken = (tokenId: number) =>
  request.post<never, CreatedRoleSupplyToken>(`/v2/role-supply-tokens/global/${tokenId}/rotations`)
export const revealGlobalToken = (tokenId: number, verificationToken: string) =>
  request.post<never, { secret: string }>(`/v2/role-supply-tokens/global/${tokenId}/secret-reveals`, {
    verificationToken,
  })
export const revokeGlobalToken = (tokenId: number) =>
  request.post<never, void>(`/v2/role-supply-tokens/global/${tokenId}/revocations`)
export const deleteGlobalToken = (tokenId: number) =>
  request.delete<never, void>(`/v2/role-supply-tokens/global/${tokenId}`)

export const fetchPersonalRoleContext = () =>
  request.get<never, PersonalRoleContext>('/v2/users/me/role-context')

export const fetchGovernedRoles = () => request.get<never, GovernedRoleItem[]>('/v2/role-governance/roles')
export const updateGovernedRoleScope = (roleId: number, roleScope: RoleScope, roleGroupId: number | null = null) =>
  request.put<never, GovernedRoleItem>(`/v2/role-governance/roles/${roleId}/scope`, { roleScope, roleGroupId })
