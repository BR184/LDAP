import request from '@/api/request'
import type { PermissionTreeNode } from '@/types/permission'

export function fetchPermissionTree() {
  return request.get<never, PermissionTreeNode[]>('/v1/permissions/tree')
}
