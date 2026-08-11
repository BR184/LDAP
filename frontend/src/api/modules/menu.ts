import request from '@/api/request'
import type { CreateMenuPayload, MenuItem, MenuTreeNode, UpdateMenuPayload } from '@/types/menu'

export function fetchMenuDetail(menuId: number) {
  return request.get<never, MenuItem>(`/v2/menus/${menuId}`)
}

export function createMenu(payload: CreateMenuPayload) {
  return request.post<never, MenuItem>('/v2/menus', payload)
}

export function updateMenu(menuId: number, payload: UpdateMenuPayload) {
  return request.put<never, MenuItem>(`/v2/menus/${menuId}`, payload)
}

export function deleteMenu(menuId: number) {
  return request.delete<never, void>(`/v2/menus/${menuId}`)
}

export function fetchMenuTree() {
  return request.get<never, MenuTreeNode[]>('/v2/menus/tree')
}

export function fetchCurrentUserMenuTree() {
  return request.get<never, MenuTreeNode[]>('/v2/menus/self/tree')
}
