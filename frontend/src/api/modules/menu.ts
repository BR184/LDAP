import request from '@/api/request'
import type { MenuTreeNode } from '@/types/menu'

export function fetchCurrentUserMenuTree() {
  return request.get<never, MenuTreeNode[]>('/v1/menus/self/tree')
}
