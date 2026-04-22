export interface UserItem {
  id: number
  username: string
  realName: string
  email: string | null
  mobile: string | null
  employeeNo: string | null
  deptCode: string | null
  status: number
  ldapDn: string | null
  roleCodes: string[]
}

export interface UserListQuery {
  username?: string
  deptCode?: string
  status?: number
}

export interface CreateUserPayload {
  username: string
  realName: string
  email: string
  mobile: string
  employeeNo: string
  deptCode: string
  initialPassword: string
  roleIds: number[]
}

export interface UpdateUserPayload {
  realName: string
  email: string
  mobile: string
  employeeNo: string
  deptCode: string
}

export interface AssignUserRolesPayload {
  roleIds: number[]
}

export interface ResetPasswordResult {
  resetPassword: string
}
