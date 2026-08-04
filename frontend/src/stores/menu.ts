import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchCurrentUserMenuTree } from '@/api/modules/menu'
import { flatNavigationItems } from '@/constants/navigation'
import type { MenuTreeNode } from '@/types/menu'

const ALWAYS_ALLOWED_PATHS = new Set(['/profile', '/access-tokens', '/access-tokens/new'])
const ADMIN_ROLE_CODES = new Set(['ADMIN', 'SUPER_ADMIN'])

export const useMenuStore = defineStore('menu', () => {
  const menuTree = ref<MenuTreeNode[]>([])
  const loaded = ref(false)
  const loading = ref(false)
  const adminOverride = ref(false)

  const allowedPathSet = computed(() => {
    if (adminOverride.value) {
      return new Set<string>(flatNavigationItems.map((item) => item.path).concat([...ALWAYS_ALLOWED_PATHS]))
    }

    const paths = new Set<string>(ALWAYS_ALLOWED_PATHS)
    collectPaths(menuTree.value, paths)
    return paths
  })

  const visibleNavigation = computed(() =>
    adminOverride.value
      ? flatNavigationItems
      : flatNavigationItems.filter((item) => allowedPathSet.value.has(item.path)),
  )

  async function loadMenus(force = false) {
    if (loaded.value && !force) {
      return menuTree.value
    }
    if (loading.value) {
      return menuTree.value
    }

    loading.value = true

    try {
      const menus = await fetchCurrentUserMenuTree()
      menuTree.value = menus
      loaded.value = true
      return menus
    } finally {
      loading.value = false
    }
  }

  function clearMenus() {
    menuTree.value = []
    loaded.value = false
    adminOverride.value = false
  }

  function canAccess(path: string) {
    if (adminOverride.value) {
      return true
    }
    return allowedPathSet.value.has(path)
  }

  function syncRoleAccess(roleCodes: string[] | undefined) {
    adminOverride.value = (roleCodes || []).some((roleCode) => ADMIN_ROLE_CODES.has(roleCode))
  }

  return {
    menuTree,
    loaded,
    visibleNavigation,
    adminOverride,
    loadMenus,
    clearMenus,
    canAccess,
    syncRoleAccess,
  }
})

function collectPaths(nodes: MenuTreeNode[], bucket: Set<string>) {
  for (const node of nodes) {
    if (node.path) {
      bucket.add(node.path)
    }
    if (node.children?.length) {
      collectPaths(node.children, bucket)
    }
  }
}
