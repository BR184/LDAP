import request from '@/api/request'
import type { MenuTreeNode } from '@/types/menu'

export function fetchMenuTree() {
  return request.get<never, MenuTreeNode[]>('/v1/menus/tree')
}

export function fetchCurrentUserMenuTree() {
  return request.get<never, MenuTreeNode[]>('/v1/menus/self/tree')
}
