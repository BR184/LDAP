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
  return request.get<never, RoleItem[]>('/v2/roles')
}

export function fetchRoleDetail(roleId: number) {
  return request.get<never, RoleItem>(`/v2/roles/${roleId}`)
}

export function createRole(payload: CreateRolePayload) {
  return request.post<never, RoleItem>('/v2/roles', payload)
}

export function updateRole(roleId: number, payload: UpdateRolePayload) {
  return request.put<never, RoleItem>(`/v2/roles/${roleId}`, payload)
}

export function updateRoleStatus(roleId: number, payload: UpdateRoleStatusPayload) {
  return request.put<never, void>(`/v2/roles/${roleId}/status`, payload)
}

export function deleteRole(roleId: number) {
  return request.delete<never, void>(`/v2/roles/${roleId}`)
}

export function batchDeleteRoles(payload: BatchDeleteRolesPayload) {
  return request.post<never, BatchDeleteRolesResult>('/v2/roles/batch-delete', payload)
}

export function fetchRolePermissionIds(roleId: number) {
  return request.get<never, number[]>(`/v2/roles/${roleId}/permissions`)
}

export function grantRolePermissions(roleId: number, payload: GrantRolePermissionsPayload) {
  return request.put<never, void>(`/v2/roles/${roleId}/permissions`, payload)
}
