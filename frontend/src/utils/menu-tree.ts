import type { MenuTreeNode } from '@/types/menu'

const ROOT_MENU_CODE = 'UINIT0'
const ROOT_MENU_NAME = 'uinit0'

export function normalizeMenuTreeForDisplay(nodes: MenuTreeNode[]): MenuTreeNode[] {
  if (!nodes?.length) {
    return []
  }

  if (nodes.length === 1 && isRootMenu(nodes[0])) {
    return nodes[0].children || []
  }

  return nodes.filter((node) => !isRootMenu(node))
}

function isRootMenu(node: MenuTreeNode | undefined) {
  if (!node) {
    return false
  }
  return node.menuCode === ROOT_MENU_CODE || node.menuName.toLowerCase() === ROOT_MENU_NAME
}
