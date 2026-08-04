import request from '@/api/request'
import type { CurrentUser, ForgotPasswordPayload, LoginCommand, LoginResult } from '@/types/auth'
import type { CurrentCapabilities } from '@/types/capability'

export function login(payload: LoginCommand) {
  return request.post<never, LoginResult>('/v1/auth/login', payload)
}

export function fetchCurrentUser() {
  return request.get<never, CurrentUser>('/v1/auth/me')
}

export function fetchCurrentCapabilities() {
  return request.get<never, CurrentCapabilities>('/v1/auth/capabilities')
}

export function forgotPassword(payload: ForgotPasswordPayload) {
  return request.post<never, string>('/v1/auth/password/forgot', payload)
}
