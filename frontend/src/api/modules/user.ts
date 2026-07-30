import request from '@/api/request'
import type {
  AssignUserRolesPayload,
  BatchDeleteUsersPayload,
  BatchDeleteUsersResult,
  ChangePasswordPayload,
  CreateUserPayload,
  UpdateUserPayload,
  VerifyPasswordPayload,
  VerifyPasswordResult,
  UserItem,
  UserListQuery,
} from '@/types/user'

export function fetchUsers(params: UserListQuery = {}) {
  return request.get<never, UserItem[]>('/v1/users', { params })
}

export function fetchUserDetail(userId: number) {
  return request.get<never, UserItem>(`/v1/users/${userId}`)
}

export function createUser(payload: CreateUserPayload) {
  return request.post<never, UserItem>('/v1/users', payload)
}

export function updateUser(userId: number, payload: UpdateUserPayload) {
  return request.put<never, UserItem>(`/v1/users/${userId}`, payload)
}

export function updateUserAccess(userId: number, accessAllowed: boolean) {
  return request.put<never, void>(`/v1/users/${userId}/access`, { accessAllowed })
}

export function deleteUser(userId: number) {
  return request.delete<never, void>(`/v1/users/${userId}`)
}

export function batchDeleteUsers(payload: BatchDeleteUsersPayload) {
  return request.post<never, BatchDeleteUsersResult>('/v1/users/batch-delete', payload, {
    timeout: 60000,
  })
}

export function resetUserPassword(userId: number) {
  return request.put<never, void>(`/v1/users/${userId}/password/reset`)
}

export function changeMyPassword(payload: ChangePasswordPayload) {
  return request.put<never, void>('/v1/users/me/password', payload)
}

export function verifyMyPassword(payload: VerifyPasswordPayload) {
  return request.post<never, VerifyPasswordResult>('/v1/users/me/password/verify', payload)
}

export function assignUserRoles(userId: number, payload: AssignUserRolesPayload) {
  return request.put<never, void>(`/v1/users/${userId}/roles`, payload)
}

export function syncUserToLdap(userId: number) {
  return request.post<never, UserItem>(`/v1/users/${userId}/sync-ldap`)
}
