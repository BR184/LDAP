import request from '@/api/request'
import type { RoleOption } from '@/types/role'

export function fetchRoles() {
  return request.get<never, RoleOption[]>('/v1/roles')
}
