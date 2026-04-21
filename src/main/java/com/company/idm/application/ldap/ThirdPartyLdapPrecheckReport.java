package com.company.idm.application.ldap;

import java.util.List;

/**
 * 描述第三方 LDAP 联调预检报告。
 */
public record ThirdPartyLdapPrecheckReport(
    String systemCode,
    String mode,
    String userFilter,
    ThirdPartyLdapCheckStatus overallStatus,
    List<ThirdPartyLdapPrecheckItem> items
) {
}
