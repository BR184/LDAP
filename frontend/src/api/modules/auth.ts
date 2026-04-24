import request from '@/api/request'
import type { CurrentUser, ForgotPasswordPayload, LoginCommand, LoginResult } from '@/types/auth'

export function login(payload: LoginCommand) {
  return request.post<never, LoginResult>('/v1/auth/login', payload)
}

export function fetchCurrentUser() {
  return request.get<never, CurrentUser>('/v1/auth/me')
}

export function forgotPassword(payload: ForgotPasswordPayload) {
  return request.post<never, string>('/v1/auth/password/forgot', payload)
}
