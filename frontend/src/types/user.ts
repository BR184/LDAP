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
  departmentPath: string | null
  jobTitle: string | null
  directLeaderRaw: string | null
  leaderRef: string | null
  accountStatus: string | null
  partTimeDepartments: DepartmentReference[]
  permissionLevel: number
  accessAllowed: boolean
  employmentStatus: string | null
  ldapDn: string | null
  roleCodes: string[]
  canResetPassword: boolean
}

export interface UserListQuery {
  /** 精确用户ID（第三方身份目录/兼容用） */
  userId?: string
  /** 模糊关键词（跨 userId/realName/employeeNo） */
  keyword?: string
  deptName?: string
  accessAllowed?: boolean
}

/** V2 用户分页查询参数（GET /api/v2/users） */
export interface UserListQueryV2 {
  keyword?: string
  userId?: string
  realName?: string
  employeeNo?: string
  mobile?: string
  email?: string
  intranetEmail?: string
  jobTitle?: string
  /** 部门规则 JSON 数组字符串 */
  departmentRules?: string
  accessAllowed?: boolean
  employmentStatus?: string
  accountStatus?: string
  sourceType?: string
  roleCodes?: string[]
  createdStart?: string
  createdEnd?: string
  pageNum?: number
  pageSize?: number
  orderBy?: string
  orderDir?: 'asc' | 'desc'
}

/** V2 统一分页结果 */
export interface PageResult<T> {
  items: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface DepartmentReference {
  deptCode: string
  deptName: string | null
  departmentPath: string | null
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
  accessAllowed: boolean
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
  expectedRoleIds?: number[]
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

export interface BatchDeleteUsersResult {
  totalCount: number
  deletedCount: number
}
