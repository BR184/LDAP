export interface LdapFrameworkDetail {
  mode: string
  baseDn: string
  userBase: string
  groupBase: string
  loginAttr: string
  userFilter: string
  authorizationMode: string
  supportedSystems: string[]
}

export interface LdapPrecheckItem {
  code: string
  name: string
  status: 'PASS' | 'FAIL' | 'SKIPPED'
  detail: string
}

export interface LdapPrecheckReport {
  systemCode: string | null
  mode: string
  userFilter: string
  overallStatus: 'PASS' | 'FAIL' | 'SKIPPED'
  items: LdapPrecheckItem[]
}

export interface LdapPrecheckPayload {
  systemCode?: string | null
  enabledUsername: string
  disabledUsername?: string | null
}
