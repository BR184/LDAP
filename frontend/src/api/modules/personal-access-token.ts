import request from '@/api/request'
import type {
  CreatePersonalAccessTokenPayload,
  CreatedPersonalAccessToken,
  PersonalAccessTokenPage,
  TokenPermission,
} from '@/types/personal-access-token'

export function fetchPersonalAccessTokens(page = 1, pageSize = 20) {
  return request.get<never, PersonalAccessTokenPage>('/v1/personal-access-tokens', {
    params: { page, pageSize },
  })
}

export function fetchAvailableTokenPermissions() {
  return request.get<never, TokenPermission[]>('/v1/personal-access-tokens/available-permissions')
}

export function createPersonalAccessToken(payload: CreatePersonalAccessTokenPayload) {
  return request.post<never, CreatedPersonalAccessToken>('/v1/personal-access-tokens', payload)
}

export function revokePersonalAccessToken(tokenId: number) {
  return request.delete<never, void>(`/v1/personal-access-tokens/${tokenId}`)
}
