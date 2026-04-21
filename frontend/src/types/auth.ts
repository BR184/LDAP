export interface LoginCommand {
  username: string
  password: string
}

export interface LoginResult {
  userId: number
  username: string
  roleCodes: string[]
  accessToken: string
  expiresAt: string
}

export interface CurrentUser {
  userId: number
  username: string
  realName: string
  email: string | null
  mobile: string | null
  deptCode: string | null
  roleCodes: string[]
}
