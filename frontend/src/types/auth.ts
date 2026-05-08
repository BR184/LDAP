export interface LoginCommand {
  loginId: string
  password: string
}

export interface ForgotPasswordPayload {
  loginId: string
}

export interface LoginResult {
  id: number
  userId: string
  roleCodes: string[]
  accessToken: string
  expiresAt: string
}

export interface CurrentUser {
  id: number
  userId: string
  realName: string
  email: string | null
  intranetEmail: string | null
  mobile: string | null
  employeeNo: string | null
  deptCode: string | null
  roleCodes: string[]
}
