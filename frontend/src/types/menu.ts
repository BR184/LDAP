export interface MenuTreeNode {
  id: number
  menuCode: string
  menuName: string
  menuType: string
  path: string
  component: string
  icon: string
  parentId: number
  sortNo: number
  children: MenuTreeNode[]
}
