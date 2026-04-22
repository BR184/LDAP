import request from '@/api/request'
import type {
  AssignUserRolesPayload,
  CreateUserPayload,
  ResetPasswordResult,
  UpdateUserPayload,
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

export function updateUserStatus(userId: number, statusCode: number) {
  return request.put<never, void>(`/v1/users/${userId}/status`, { statusCode })
}

export function deleteUser(userId: number) {
  return request.delete<never, void>(`/v1/users/${userId}`)
}

export function resetUserPassword(userId: number) {
  return request.put<never, ResetPasswordResult>(`/v1/users/${userId}/password/reset`)
}

export function assignUserRoles(userId: number, payload: AssignUserRolesPayload) {
  return request.put<never, void>(`/v1/users/${userId}/roles`, payload)
}

export function syncUserToLdap(userId: number) {
  return request.post<never, UserItem>(`/v1/users/${userId}/sync-ldap`)
}

export function syncUsersFromFeishu() {
  return request.post<never, { batch: { batchNo: string } }>('/v1/users/sync/feishu', {})
}
