import request from '@/api/request'
import type { DepartmentTreeNode } from '@/types/department'

export function fetchDepartmentTree() {
  return request.get<never, DepartmentTreeNode[]>('/v1/departments/tree')
}
