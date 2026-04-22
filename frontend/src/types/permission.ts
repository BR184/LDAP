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
