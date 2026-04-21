package com.company.idm.application.ldap;

/**
 * 描述第三方 LDAP 联调预检请求。
 */
public record ThirdPartyLdapPrecheckCommand(
    String systemCode,
    String enabledUsername,
    String disabledUsername
) {
}
