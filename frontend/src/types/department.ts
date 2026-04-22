export interface DepartmentTreeNode {
  deptCode: string
  deptName: string
  parentDeptCode: string | null
  ancestorPath: string
  deptLevel: number
  status: number
  children: DepartmentTreeNode[]
}

export interface DepartmentDetail {
  id: number
  deptCode: string
  deptName: string
  parentDeptCode: string | null
  ancestorPath: string
  deptLevel: number
  sourceType: string
  externalId: string | null
  ldapDn: string | null
  status: number
}

export interface DepartmentTreeOption {
  value: string
  label: string
  disabled?: boolean
  children?: DepartmentTreeOption[]
}

export interface CreateDepartmentPayload {
  deptCode: string
  deptName: string
  parentDeptCode?: string | null
  externalId?: string | null
}

export interface UpdateDepartmentPayload {
  deptName: string
  parentDeptCode?: string | null
  status: number
}
