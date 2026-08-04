import type { RolePermissionBundle } from '@/types/permission'

export type PermissionSuiteLevel = 'NONE' | 'STANDARD' | 'ADMIN' | 'CUSTOM'
export type SelectablePermissionSuiteLevel = Exclude<PermissionSuiteLevel, 'CUSTOM'>

export interface PermissionSuiteCategory {
  categoryId: string
  categoryName: string
  categoryDescription: string
  categorySort: number
  standard: RolePermissionBundle
  admin: RolePermissionBundle
}

export function buildPermissionSuiteCategories(
  bundles: RolePermissionBundle[],
): PermissionSuiteCategory[] {
  const grouped = new Map<string, RolePermissionBundle[]>()
  for (const bundle of bundles) {
    const current = grouped.get(bundle.categoryId) || []
    current.push(bundle)
    grouped.set(bundle.categoryId, current)
  }

  return [...grouped.values()]
    .map((moduleBundles) => {
      const standard = moduleBundles.find((bundle) => bundle.tier === 'STANDARD')
      const admin = moduleBundles.find((bundle) => bundle.tier === 'ADMIN')
      if (!standard || !admin) {
        throw new Error(`权限套件模块 ${moduleBundles[0]?.categoryId || 'UNKNOWN'} 缺少常规或完整管理定义`)
      }
      return {
        categoryId: standard.categoryId,
        categoryName: standard.categoryName,
        categoryDescription: standard.categoryDescription,
        categorySort: standard.categorySort,
        standard,
        admin,
      }
    })
    .sort((left, right) => left.categorySort - right.categorySort)
}

export function resolvePermissionSuiteLevel(
  category: PermissionSuiteCategory,
  selectedPermissionIds: number[],
): PermissionSuiteLevel {
  const selected = new Set(selectedPermissionIds)
  if (category.admin.permissionIds.every((permissionId) => selected.has(permissionId))) {
    return 'ADMIN'
  }

  const standardIds = new Set(category.standard.permissionIds)
  const hasCompleteStandard = category.standard.permissionIds.every((permissionId) => selected.has(permissionId))
  const hasAdminOnlyPermission = category.admin.permissionIds.some(
    (permissionId) => !standardIds.has(permissionId) && selected.has(permissionId),
  )
  if (hasCompleteStandard && !hasAdminOnlyPermission) {
    return 'STANDARD'
  }

  const hasAnyModulePermission = category.admin.permissionIds.some((permissionId) => selected.has(permissionId))
  return hasAnyModulePermission ? 'CUSTOM' : 'NONE'
}

export function applyPermissionSuiteLevel(
  categories: PermissionSuiteCategory[],
  categoryId: string,
  level: SelectablePermissionSuiteLevel,
  selectedPermissionIds: number[],
): number[] {
  const target = categories.find((category) => category.categoryId === categoryId)
  if (!target) {
    throw new Error(`权限套件模块不存在：${categoryId}`)
  }

  const selected = new Set(selectedPermissionIds)
  const sharedSelectedIds = new Set<number>()
  for (const category of categories) {
    if (category.categoryId === categoryId) continue
    for (const permissionId of category.admin.permissionIds) {
      if (selected.has(permissionId)) sharedSelectedIds.add(permissionId)
    }
  }

  for (const permissionId of target.admin.permissionIds) {
    if (!sharedSelectedIds.has(permissionId)) selected.delete(permissionId)
  }

  const selectedBundle = level === 'STANDARD' ? target.standard : level === 'ADMIN' ? target.admin : null
  selectedBundle?.permissionIds.forEach((permissionId) => selected.add(permissionId))

  return [...selected].sort((left, right) => left - right)
}
