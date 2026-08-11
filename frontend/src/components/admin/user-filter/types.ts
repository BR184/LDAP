import type { UserListQueryV2 } from '@/types/user'

/** 部门规则三要素（对齐 lumen_flow 模型日志筛选） */
export type RuleMode = 'include' | 'exclude'
export type TreeScope = 'EXACT' | 'SUBTREE'
export type MembershipScope = 'ANY' | 'PRIMARY' | 'PART_TIME'

export interface DepartmentRuleDraft {
  /** 本地唯一标识（非后端字段） */
  id: string
  mode: RuleMode
  deptCode: string
  deptName: string
  /** 部门完整路径（如「产品研发一部 / 三组」），仅用于展示消歧 */
  deptPath?: string
  treeScope: TreeScope
  membershipScope: MembershipScope
  /** 未归属部门特殊规则 */
  unassigned?: boolean
}

/** 用户列表筛选状态（快速 + 精确共用一份数据） */
export interface UserFilters {
  /** 快速：综合关键词 */
  keyword: string
  /** 快速：在职状态 */
  employmentStatus: '' | 'ACTIVE' | 'RESIGNED'
  /** 快速：允许使用 */
  accessAllowed: '' | 'true' | 'false'
  /** 精确：用户ID */
  userId: string
  /** 精确：姓名 */
  realName: string
  /** 精确：工号 */
  employeeNo: string
  /** 精确：手机 */
  mobile: string
  /** 精确：邮箱 */
  email: string
  /** 精确：内网邮箱 */
  intranetEmail: string
  /** 精确：职务 */
  jobTitle: string
  /** 精确：账号状态 */
  accountStatus: string
  /** 精确：来源 */
  sourceType: string
  /** 精确：角色多选 */
  roleCodes: string[]
  /** 精确：创建时间范围 [start, end] */
  createdRange: [string, string] | []
  /** 精确：部门规则 */
  departmentRules: DepartmentRuleDraft[]
}

export function defaultUserFilters(): UserFilters {
  return {
    keyword: '',
    employmentStatus: '',
    accessAllowed: '',
    userId: '',
    realName: '',
    employeeNo: '',
    mobile: '',
    email: '',
    intranetEmail: '',
    jobTitle: '',
    accountStatus: '',
    sourceType: '',
    roleCodes: [],
    createdRange: [],
    departmentRules: [],
  }
}

function toJsonParam(rules: DepartmentRuleDraft[]): string | undefined {
  if (!rules.length) return undefined
  return JSON.stringify(
    rules.map((rule) =>
      rule.unassigned
        ? { mode: rule.mode, unassigned: true }
        : {
            mode: rule.mode,
            deptCode: rule.deptCode,
            treeScope: rule.treeScope,
            membershipScope: rule.membershipScope,
          },
    ),
  )
}

/** 将筛选状态序列化为 V2 /users 查询参数 */
export function filtersToV2Query(filters: UserFilters): UserListQueryV2 {
  return {
    keyword: filters.keyword.trim() || undefined,
    employmentStatus: filters.employmentStatus || undefined,
    accessAllowed: filters.accessAllowed === 'true' ? true : filters.accessAllowed === 'false' ? false : undefined,
    userId: filters.userId.trim() || undefined,
    realName: filters.realName.trim() || undefined,
    employeeNo: filters.employeeNo.trim() || undefined,
    mobile: filters.mobile.trim() || undefined,
    email: filters.email.trim() || undefined,
    intranetEmail: filters.intranetEmail.trim() || undefined,
    jobTitle: filters.jobTitle.trim() || undefined,
    accountStatus: filters.accountStatus || undefined,
    sourceType: filters.sourceType || undefined,
    roleCodes: filters.roleCodes.length ? filters.roleCodes : undefined,
    createdStart: filters.createdRange?.[0],
    createdEnd: filters.createdRange?.[1],
    departmentRules: toJsonParam(filters.departmentRules),
  }
}

/** 统计已应用的条件数量（用于标签条/按钮角标） */
export function countActiveFilters(filters: UserFilters): number {
  let count = 0
  if (filters.keyword.trim()) count += 1
  if (filters.employmentStatus) count += 1
  if (filters.accessAllowed) count += 1
  if (filters.userId.trim()) count += 1
  if (filters.realName.trim()) count += 1
  if (filters.employeeNo.trim()) count += 1
  if (filters.mobile.trim()) count += 1
  if (filters.email.trim()) count += 1
  if (filters.intranetEmail.trim()) count += 1
  if (filters.jobTitle.trim()) count += 1
  if (filters.accountStatus) count += 1
  if (filters.sourceType) count += 1
  if (filters.roleCodes.length) count += 1
  if (filters.createdRange?.length) count += 1
  if (filters.departmentRules.length) count += 1
  return count
}

let ruleIdSeed = 0
export function nextRuleId(): string {
  ruleIdSeed += 1
  return `rule_${Date.now()}_${ruleIdSeed}`
}
