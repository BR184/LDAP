export interface UserItem {
  id: number
  userId: string
  realName: string
  email: string | null
  intranetEmail: string | null
  mobile: string | null
  employeeNo: string | null
  deptName: string | null
  deptCode: string | null
  jobTitle: string | null
  directLeaderRaw: string | null
  leaderRef: string | null
  accountStatus: string | null
  partTimeDeptCodes: string[]
  partTimeDeptNames: string[]
  permissionLevel: number
  status: number
  employmentStatus: string | null
  ldapDn: string | null
  roleCodes: string[]
}

export interface UserListQuery {
  userId?: string
  deptName?: string
  status?: number
}

export interface CreateUserPayload {
  userId: string
  realName: string
  email?: string
  intranetEmail: string
  mobile: string
  employeeNo: string
  deptCode: string
  partTimeDeptCodes: string[]
  initialPassword: string
  roleIds: number[]
}

export interface UpdateUserPayload {
  userId: string
  realName: string
  email?: string
  intranetEmail: string
  mobile: string
  employeeNo: string
  deptCode: string
  partTimeDeptCodes: string[]
}

export interface AssignUserRolesPayload {
  roleIds: number[]
}

export interface ChangePasswordPayload {
  verificationToken: string
  newPassword: string
  confirmPassword: string
}

export interface VerifyPasswordPayload {
  oldPassword: string
}

export interface VerifyPasswordResult {
  verificationToken: string
}

export interface BatchDeleteUsersPayload {
  userIds: number[]
  userIdKeyword?: string
  deptNameKeyword?: string
  statusCode?: number
}

export interface BatchDeleteUsersResult {
  totalCount: number
  deletedCount: number
}
