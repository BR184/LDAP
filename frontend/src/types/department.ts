export interface DepartmentTreeNode {
  deptCode: string
  deptName: string
  parentDeptCode: string | null
  ancestorPath: string
  deptLevel: number
  status: number
  children: DepartmentTreeNode[]
}

export interface DepartmentTreeOption {
  value: string
  label: string
  disabled?: boolean
  children?: DepartmentTreeOption[]
}
