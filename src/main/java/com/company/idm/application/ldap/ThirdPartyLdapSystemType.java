package com.company.idm.application.ldap;

import com.company.idm.common.exception.BizException;
import java.util.Arrays;

/**
 * 定义当前支持的第三方系统类型。
 */
public enum ThirdPartyLdapSystemType {
    GITLAB("gitlab", "GitLab", "docs/templates/gitlab-ldap-template.md", "{login}"),
    JENKINS("jenkins", "Jenkins", "docs/templates/jenkins-ldap-template.md", "{0}"),
    NEXUS("nexus", "Nexus", "docs/templates/nexus-ldap-template.md", "{login}"),
    ZENTAO("zentao", "禅道", "docs/templates/zentao-ldap-template.md", "{login}");

    private final String code;
    private final String displayName;
    private final String documentPath;
    private final String filterPlaceholder;

    ThirdPartyLdapSystemType(String code, String displayName, String documentPath, String filterPlaceholder) {
        this.code = code;
        this.displayName = displayName;
        this.documentPath = documentPath;
        this.filterPlaceholder = filterPlaceholder;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public String buildUserFilter() {
        return "(&(objectClass=inetOrgPerson)(uid=" + filterPlaceholder + ")(employeeType=ENABLED))";
    }

    public String buildTemplateUserFilter() {
        return switch (this) {
            case GITLAB -> "(&(objectClass=inetOrgPerson)(employeeType=ENABLED))";
            default -> buildUserFilter();
        };
    }

    public static ThirdPartyLdapSystemType fromCode(String code) {
        return Arrays.stream(values())
            .filter(item -> item.code.equalsIgnoreCase(code))
            .findFirst()
            .orElseThrow(() -> new BizException("LDAP_TEMPLATE_SYSTEM_UNSUPPORTED", "暂不支持该第三方 LDAP 模板"));
    }
}
