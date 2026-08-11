import request from '@/api/request'
import type {
  CreatePersonalAccessTokenPayload,
  CreatedPersonalAccessToken,
  PersonalAccessTokenPage,
  PersonalAccessTokenSecretResponse,
  TokenPermission,
  TokenPermissionGroup,
} from '@/types/personal-access-token'

export function fetchPersonalAccessTokens(page = 1, pageSize = 20) {
  return request.get<never, PersonalAccessTokenPage>('/v2/personal-access-tokens', {
    params: { page, pageSize },
  })
}

export function fetchAvailableTokenPermissions() {
  return request.get<never, TokenPermission[]>('/v2/personal-access-tokens/available-permissions')
}

export function fetchAvailableTokenPermissionGroups() {
  return request.get<never, TokenPermissionGroup[]>('/v2/personal-access-tokens/available-permission-groups')
}

export function createPersonalAccessToken(payload: CreatePersonalAccessTokenPayload) {
  return request.post<never, CreatedPersonalAccessToken>('/v2/personal-access-tokens', payload)
}

export function revealPersonalAccessToken(tokenId: number) {
  return request.get<never, PersonalAccessTokenSecretResponse>(
    `/v2/personal-access-tokens/${tokenId}/secret`,
  )
}

export function rotatePersonalAccessToken(tokenId: number) {
  return request.post<never, CreatedPersonalAccessToken>(
    `/v2/personal-access-tokens/${tokenId}/rotations`,
  )
}

export function revokePersonalAccessToken(tokenId: number) {
  return request.post<never, void>(`/v2/personal-access-tokens/${tokenId}/revocations`)
}

export function deletePersonalAccessToken(tokenId: number) {
  return request.delete<never, void>(`/v2/personal-access-tokens/${tokenId}`)
}
