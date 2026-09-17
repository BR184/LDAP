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

export type SubscriptionStatus = 'ENABLED' | 'DISABLED'

export interface SubscriptionItem {
  id: number
  name: string
  description: string | null
  subjectType: 'ROLE_GROUP' | 'GLOBAL'
  subjectId: number | null
  status: SubscriptionStatus
  roleCount: number
  creator: string
  gmtCreate: string
  gmtModified: string
}

export interface SubscriptionMqInfo {
  host: string
  port: number
  vhost: string
  queue: string
  username: string
  password: string
}

export interface SubscriptionCredential {
  subscriptionId: number
  tokenSecret: string
  mq: SubscriptionMqInfo
}

export interface CreatedSubscription {
  subscription: SubscriptionItem
  credential: SubscriptionCredential
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
