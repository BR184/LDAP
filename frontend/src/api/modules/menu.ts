import request from '@/api/request'
import type { CreateMenuPayload, MenuItem, MenuTreeNode, UpdateMenuPayload } from '@/types/menu'

export function fetchMenuDetail(menuId: number) {
  return request.get<never, MenuItem>(`/v1/menus/${menuId}`)
}

export function createMenu(payload: CreateMenuPayload) {
  return request.post<never, MenuItem>('/v1/menus', payload)
}

export function updateMenu(menuId: number, payload: UpdateMenuPayload) {
  return request.put<never, MenuItem>(`/v1/menus/${menuId}`, payload)
}

export function deleteMenu(menuId: number) {
  return request.delete<never, void>(`/v1/menus/${menuId}`)
}

export function fetchMenuTree() {
  return request.get<never, MenuTreeNode[]>('/v1/menus/tree')
}

export function fetchCurrentUserMenuTree() {
  return request.get<never, MenuTreeNode[]>('/v1/menus/self/tree')
}
