import request from '@/api/request'
import type {
  AssignUserRolesPayload,
  BatchDeleteUsersResult,
  ChangePasswordPayload,
  CreateUserPayload,
  UpdateUserPayload,
  VerifyPasswordPayload,
  VerifyPasswordResult,
  UserItem,
  UserListQuery,
  UserListQueryV2,
  PageResult,
} from '@/types/user'

/** V1 兼容：列表模糊搜索（仅供存量/第三方，新功能请用 fetchUsersV2） */
export function fetchUsers(params: UserListQuery = {}) {
  return request.get<never, UserItem[]>('/v1/users', { params })
}

/** V2 用户分页查询（组合筛选 + 服务端分页） */
export function fetchUsersV2(params: UserListQueryV2 = {}) {
  return request.get<never, PageResult<UserItem>>('/v2/users', { params })
}

/** V2 按业务用户ID精确查询（第三方身份目录用） */
export function fetchUserByUserId(userId: string) {
  return request.get<never, UserItem>(`/v2/users/by-user-id/${encodeURIComponent(userId)}`)
}

export function fetchUserDetail(userId: number) {
  return request.get<never, UserItem>(`/v2/users/${userId}`)
}

export function createUser(payload: CreateUserPayload) {
  return request.post<never, UserItem>('/v2/users', payload)
}

export function updateUser(userId: number, payload: UpdateUserPayload) {
  return request.put<never, UserItem>(`/v2/users/${userId}`, payload)
}

export function updateUserAccess(userId: number, accessAllowed: boolean) {
  return request.put<never, void>(`/v2/users/${userId}/access`, { accessAllowed })
}

export function deleteUser(userId: number) {
  return request.delete<never, void>(`/v2/users/${userId}`)
}

export function batchDeleteUsers(userIds: number[]) {
  return request.post<never, BatchDeleteUsersResult>('/v2/users/batch-delete', { userIds }, {
    timeout: 60000,
  })
}

export function resetUserPassword(userId: number) {
  return request.put<never, void>(`/v2/users/${userId}/password/reset`)
}

export function changeMyPassword(payload: ChangePasswordPayload) {
  return request.put<never, void>('/v2/users/me/password', payload)
}

export function verifyMyPassword(payload: VerifyPasswordPayload) {
  return request.post<never, VerifyPasswordResult>('/v2/users/me/password/verify', payload)
}

export function assignUserRoles(userId: number, payload: AssignUserRolesPayload) {
  return request.put<never, void>(`/v2/users/${userId}/roles`, payload)
}

export function syncUserToLdap(userId: number) {
  return request.post<never, UserItem>(`/v2/users/${userId}/sync-ldap`)
}
