package com.company.idm.application.ldap;

import java.util.List;

/**
 * 描述第三方 LDAP 通用接入框架的标准契约。
 */
public record ThirdPartyLdapFrameworkDetail(
    String mode,
    String baseDn,
    String userBase,
    String groupBase,
    String loginAttr,
    String userFilter,
    String authorizationMode,
    List<String> supportedSystems
) {
}
