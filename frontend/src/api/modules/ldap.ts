import request from '@/api/request'
import type { LdapFrameworkDetail, LdapPrecheckPayload, LdapPrecheckReport } from '@/types/ldap'

export function fetchLdapFramework() {
  return request.get<never, LdapFrameworkDetail>('/v1/ldap/framework')
}

export function executeLdapPrecheck(payload: LdapPrecheckPayload) {
  return request.post<never, LdapPrecheckReport>('/v1/ldap/precheck', payload)
}
