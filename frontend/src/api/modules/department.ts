import request from '@/api/request'
import type {
  CreateDepartmentPayload,
  DepartmentDetail,
  DepartmentTreeNode,
  UpdateDepartmentPayload,
} from '@/types/department'

export function fetchDepartmentTree() {
  return request.get<never, DepartmentTreeNode[]>('/v1/departments/tree')
}

export function fetchDepartmentDetail(deptCode: string) {
  return request.get<never, DepartmentDetail>(`/v1/departments/${deptCode}`)
}

export function createDepartment(payload: CreateDepartmentPayload) {
  return request.post<never, DepartmentDetail>('/v1/departments', payload)
}

export function updateDepartment(deptCode: string, payload: UpdateDepartmentPayload) {
  return request.put<never, DepartmentDetail>(`/v1/departments/${deptCode}`, payload)
}

export function deleteDepartment(deptCode: string) {
  return request.delete<never, void>(`/v1/departments/${deptCode}`)
}

export function syncDepartmentToLdap(deptCode: string) {
  return request.post<never, DepartmentDetail>(`/v1/departments/${deptCode}/sync-ldap`)
}
