package com.company.idm.application.ldap;

/**
 * 描述单个预检项结果。
 */
public record ThirdPartyLdapPrecheckItem(
    String code,
    String name,
    ThirdPartyLdapCheckStatus status,
    String detail
) {
}
