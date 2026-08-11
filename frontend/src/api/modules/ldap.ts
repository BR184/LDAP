import request from '@/api/request'
import type { LdapFrameworkDetail, LdapPrecheckPayload, LdapPrecheckReport } from '@/types/ldap'

export function fetchLdapFramework() {
  return request.get<never, LdapFrameworkDetail>('/v2/ldap/framework')
}

export function executeLdapPrecheck(payload: LdapPrecheckPayload) {
  return request.post<never, LdapPrecheckReport>('/v2/ldap/precheck', payload)
}
