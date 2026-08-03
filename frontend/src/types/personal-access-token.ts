export type PersonalAccessTokenStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED'

export interface TokenPermission {
  id: number
  permissionCode: string
  permissionName: string
  resourcePath: string
  action: string
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
}

export interface PersonalAccessTokenPage {
  items: PersonalAccessTokenItem[]
  total: number
  page: number
  pageSize: number
}

export interface CreatePersonalAccessTokenPayload {
  name: string
  expiresAt: string | null
  permissionIds: number[]
  passwordVerificationToken: string
}

export interface CreatedPersonalAccessToken {
  token: PersonalAccessTokenItem
  secret: string
}
