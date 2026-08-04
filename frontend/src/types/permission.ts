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
}
