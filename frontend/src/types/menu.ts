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

export interface MenuItem {
  id: number
  menuCode: string
  menuName: string
  parentId: number
  menuType: string
  path: string
  component: string
  icon: string
  sortNo: number
  remark: string | null
}

export interface MenuTreeOption {
  value: number
  label: string
  children: MenuTreeOption[]
}

export interface CreateMenuPayload {
  menuCode: string
  menuName: string
  parentId: number
  menuType: string
  path: string
  component?: string | null
  icon?: string | null
  sortNo: number
  remark?: string | null
}

export interface UpdateMenuPayload {
  menuName: string
  parentId: number
  menuType: string
  path: string
  component?: string | null
  icon?: string | null
  sortNo: number
  remark?: string | null
}
