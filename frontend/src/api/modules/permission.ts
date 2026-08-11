import request from '@/api/request'
import type { PermissionTreeNode, RolePermissionBundle } from '@/types/permission'

export function fetchPermissionTree() {
  return request.get<never, PermissionTreeNode[]>('/v2/permissions/tree')
}

export function fetchRolePermissionBundles() {
  return request.get<never, RolePermissionBundle[]>('/v2/permissions/bundles')
}
