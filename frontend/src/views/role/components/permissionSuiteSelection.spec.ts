import { describe, expect, it } from 'vitest'
import type { RolePermissionBundle } from '@/types/permission'
import {
  applyPermissionSuiteLevel,
  buildPermissionSuiteCategories,
  resolvePermissionSuiteLevel,
} from './permissionSuiteSelection'

const bundles: RolePermissionBundle[] = [
  bundle('USER_STANDARD', 'USER_MANAGEMENT', 'STANDARD', [1, 2], 10),
  bundle('USER_ADMIN', 'USER_MANAGEMENT', 'ADMIN', [1, 2, 3, 4], 10),
  bundle('ROLE_STANDARD', 'ROLE_MANAGEMENT', 'STANDARD', [2, 5], 20),
  bundle('ROLE_ADMIN', 'ROLE_MANAGEMENT', 'ADMIN', [2, 5, 6], 20),
]
const categories = buildPermissionSuiteCategories(bundles)
const users = categories.find((category) => category.categoryId === 'USER_MANAGEMENT')!

describe('permission suite selection', () => {
  it('detects ADMIN before its STANDARD subset', () => {
    expect(resolvePermissionSuiteLevel(users, [1, 2, 3, 4])).toBe('ADMIN')
    expect(resolvePermissionSuiteLevel(users, [1, 2])).toBe('STANDARD')
  })

  it('marks partial module permissions as CUSTOM', () => {
    expect(resolvePermissionSuiteLevel(users, [1, 3])).toBe('CUSTOM')
    expect(resolvePermissionSuiteLevel(users, [])).toBe('NONE')
  })

  it('downgrades ADMIN to STANDARD without touching unrelated granular permissions', () => {
    expect(applyPermissionSuiteLevel(categories, 'USER_MANAGEMENT', 'STANDARD', [1, 2, 3, 4, 99]))
      .toEqual([1, 2, 99])
  })

  it('keeps shared permissions required by another configured module', () => {
    expect(applyPermissionSuiteLevel(categories, 'USER_MANAGEMENT', 'NONE', [1, 2, 5, 99]))
      .toEqual([2, 5, 99])
  })

  it('adds the complete ADMIN permission set', () => {
    expect(applyPermissionSuiteLevel(categories, 'USER_MANAGEMENT', 'ADMIN', [5, 99]))
      .toEqual([1, 2, 3, 4, 5, 99])
  })
})

function bundle(
  id: string,
  categoryId: string,
  tier: 'STANDARD' | 'ADMIN',
  permissionIds: number[],
  categorySort: number,
): RolePermissionBundle {
  return {
    id,
    name: id,
    sort: tier === 'STANDARD' ? 1 : 2,
    permissionIds,
    permissionCodes: permissionIds.map(String),
    categoryId,
    categoryName: categoryId,
    categoryDescription: `${categoryId} description`,
    categorySort,
    tier,
  }
}
