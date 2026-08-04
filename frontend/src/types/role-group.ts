export type RoleScope = 'GLOBAL' | 'GROUP' | 'SYSTEM'
export type RoleGroupMemberRole = 'OWNER' | 'MANAGER'

export interface RoleGroupItem {
  id: number
  groupName: string
  remark: string | null
  status: number
  currentMemberRole: RoleGroupMemberRole | null
  creatorName: string | null
  ownerNames: string[]
  memberCount: number
  roleCount: number
  gmtCreate: string
  gmtModified: string
}

export interface RoleGroupMemberItem {
  userId: number
  realName: string
  memberRole: RoleGroupMemberRole
}

export interface RoleGroupRoleItem {
  id: number
  roleCode: string
  roleName: string
  permissionLevel: number
  status: number
  remark: string | null
  roleScope: RoleScope
  roleGroupId: number | null
  editable: boolean
}

export interface PublicUserItem {
  id: number
  realName: string
}

export interface RoleSupplyTokenItem {
  id: number
  name: string
  description: string | null
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED'
  subjectType: 'ROLE_GROUP' | 'GLOBAL'
  subjectId: number | null
  expiresAt: string | null
  lastUsedAt: string | null
  lastUsedIp: string | null
  createdAt: string
  secretRecoverable: boolean
}

export interface RoleSupplyTokenPage {
  items: RoleSupplyTokenItem[]
  total: number
  page: number
  pageSize: number
}

export interface CreatedRoleSupplyToken {
  token: RoleSupplyTokenItem
  secret: string
}

export interface PersonalRoleContext {
  roles: Array<{
    id: number
    roleCode: string
    roleName: string
    roleScope: RoleScope
    roleGroupId: number | null
    roleGroupName: string | null
  }>
  roleGroups: Array<{
    id: number
    groupName: string
    memberRole: RoleGroupMemberRole
  }>
}

export interface GovernedRoleItem {
  id: number
  roleCode: string
  roleName: string
  permissionLevel: number
  builtIn: number
  status: number
  remark: string | null
  roleScope: RoleScope
  roleGroupId: number | null
}
