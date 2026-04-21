package com.company.idm.application.ldap;

import java.util.List;
import java.util.Map;

/**
 * 描述某个第三方系统的 LDAP 接入模板。
 */
public record ThirdPartyLdapTemplateDetail(
    String systemCode,
    String systemName,
    String documentPath,
    Map<String, String> settings,
    Map<String, String> fieldMappings,
    List<String> notes
) {
}
