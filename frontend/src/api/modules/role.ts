import request from '@/api/request'
import type {
  BatchDeleteRolesPayload,
  BatchDeleteRolesResult,
  CreateRolePayload,
  GrantRolePermissionsPayload,
  RoleItem,
  UpdateRolePayload,
  UpdateRoleStatusPayload,
} from '@/types/role'

export function fetchRoles() {
  return request.get<never, RoleItem[]>('/v1/roles')
}

export function fetchRoleDetail(roleId: number) {
  return request.get<never, RoleItem>(`/v1/roles/${roleId}`)
}

export function createRole(payload: CreateRolePayload) {
  return request.post<never, RoleItem>('/v1/roles', payload)
}

export function updateRole(roleId: number, payload: UpdateRolePayload) {
  return request.put<never, RoleItem>(`/v1/roles/${roleId}`, payload)
}

export function updateRoleStatus(roleId: number, payload: UpdateRoleStatusPayload) {
  return request.put<never, void>(`/v1/roles/${roleId}/status`, payload)
}

export function deleteRole(roleId: number) {
  return request.delete<never, void>(`/v1/roles/${roleId}`)
}

export function batchDeleteRoles(payload: BatchDeleteRolesPayload) {
  return request.post<never, BatchDeleteRolesResult>('/v1/roles/batch-delete', payload)
}

export function fetchRolePermissionIds(roleId: number) {
  return request.get<never, number[]>(`/v1/roles/${roleId}/permissions`)
}

export function grantRolePermissions(roleId: number, payload: GrantRolePermissionsPayload) {
  return request.put<never, void>(`/v1/roles/${roleId}/permissions`, payload)
}
