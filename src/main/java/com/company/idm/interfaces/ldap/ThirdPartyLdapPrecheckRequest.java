package com.company.idm.interfaces.ldap;

import jakarta.validation.constraints.NotBlank;

/**
 * 描述第三方 LDAP 联调预检入参。
 */
public record ThirdPartyLdapPrecheckRequest(
    String systemCode,
    @NotBlank(message = "启用用户样本不能为空") String enabledUsername,
    String disabledUsername
) {
}
