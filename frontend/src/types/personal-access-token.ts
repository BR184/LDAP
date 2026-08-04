export type PersonalAccessTokenStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED'
export type PersonalAccessTokenScopeMode = 'FIXED' | 'FOLLOW_ACCOUNT'
export type TokenPermissionRisk = 'LOW' | 'HIGH'

export interface TokenPermission {
  id: number
  permissionCode: string
  permissionName: string
  resourcePath: string
  action: string
}

export interface TokenPermissionGroup {
  id: string
  name: string
  risk: TokenPermissionRisk
  sort: number
  permissions: TokenPermission[]
}

export interface PersonalAccessTokenItem {
  id: number
  name: string
  tokenPrefix: string
  status: PersonalAccessTokenStatus
  expiresAt: string | null
  lastUsedAt: string | null
  lastUsedIp: string | null
  createdAt: string
  permissions: TokenPermission[]
  description: string | null
  scopeMode: PersonalAccessTokenScopeMode | null
  secretRecoverable: boolean
}

export interface PersonalAccessTokenPage {
  items: PersonalAccessTokenItem[]
  total: number
  page: number
  pageSize: number
}

export interface CreatePersonalAccessTokenPayload {
  name: string
  description: string | null
  expiresAt: string | null
  permissionIds: number[]
  scopeMode: PersonalAccessTokenScopeMode
}

export interface CreatedPersonalAccessToken {
  token: PersonalAccessTokenItem
  secret: string
}

export interface PersonalAccessTokenSecretResponse {
  secret: string
}
