export interface RoleItem {
  id: number
  roleCode: string
  roleName: string
  permissionLevel: number
  builtIn: number
  status: number
  remark: string | null
}

export type RoleOption = RoleItem

export interface CreateRolePayload {
  roleCode: string
  roleName: string
  permissionLevel: number
  remark?: string | null
}

export interface UpdateRolePayload {
  roleName: string
  permissionLevel: number
  remark?: string | null
}

export interface UpdateRoleStatusPayload {
  status: number
}

export interface BindRoleMenusPayload {
  menuIds: number[]
}

export interface GrantRolePermissionsPayload {
  permissionIds: number[]
}

export interface BatchDeleteRolesPayload {
  roleIds: number[]
}

export interface BatchDeleteRolesResult {
  totalCount: number
  deletedCount: number
}
