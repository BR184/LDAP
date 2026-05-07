import request from '@/api/request'
import type { SyncBatchSummary } from '@/types/sync'
import type {
  AssignUserRolesPayload,
  BatchDeleteUsersPayload,
  BatchDeleteUsersResult,
  ChangePasswordPayload,
  CreateUserPayload,
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

export function assignUserRoles(userId: number, payload: AssignUserRolesPayload) {
  return request.put<never, void>(`/v1/users/${userId}/roles`, payload)
}

export function syncUserToLdap(userId: number) {
  return request.post<never, UserItem>(`/v1/users/${userId}/sync-ldap`)
}

export function syncUsersFromFeishu() {
  return request.post<never, { batch: { batchNo: string } }>('/v1/users/sync/feishu', {})
}

export function importUsersFromFeishuFile(documentPath: string, remark?: string, forceFullSync = false) {
  return request.post<never, SyncBatchSummary>('/v1/users/import/feishu-file', {
    documentPath,
    remark,
    forceFullSync,
  })
}
