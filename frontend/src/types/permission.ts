export interface PermissionTreeNode {
  id: number
  permissionCode: string
  permissionName: string
  permissionType: string
  resourcePath: string
  action: string
  parentId: number
  children: PermissionTreeNode[]
}

export interface RolePermissionBundle {
  id: string
  name: string
  sort: number
  permissionIds: number[]
  permissionCodes: string[]
  categoryId: string
  categoryName: string
  categoryDescription: string
  categorySort: number
  tier: RolePermissionBundleTier
}

export type RolePermissionBundleTier = 'STANDARD' | 'ADMIN'
