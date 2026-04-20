package com.company.idm.infrastructure.ldap;

import com.company.idm.infrastructure.config.AppLdapProperties;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import org.springframework.ldap.support.LdapNameBuilder;

/**
 * 统一封装 LDAP DN 的构造与解析，避免手工拼接字符串带来的转义问题。
 */
public final class LdapDnHelper {

    private LdapDnHelper() {
    }

    public static Name buildRelativeUserDn(AppLdapProperties ldapProperties, String username) {
        return LdapNameBuilder.newInstance()
            .add("ou", normalizeOuValue(ldapProperties.getPeopleOu()))
            .add("uid", username)
            .build();
    }

    public static String buildUserDn(AppLdapProperties ldapProperties, String username) {
        return toAbsoluteDn(ldapProperties, buildRelativeUserDn(ldapProperties, username));
    }

    public static Name buildRelativeGroupDn(AppLdapProperties ldapProperties, String groupCode, String groupName) {
        return LdapNameBuilder.newInstance()
            .add("ou", normalizeOuValue(ldapProperties.getGroupsOu()))
            .add("cn", buildGroupCn(groupCode, groupName))
            .build();
    }

    public static String buildGroupDn(AppLdapProperties ldapProperties, String groupCode, String groupName) {
        return toAbsoluteDn(ldapProperties, buildRelativeGroupDn(ldapProperties, groupCode, groupName));
    }

    public static String buildPlaceholderMemberDn(AppLdapProperties ldapProperties) {
        return buildUserDn(ldapProperties, "placeholder");
    }

    public static String buildGroupCn(String groupCode, String groupName) {
        return groupCode + "_" + groupName;
    }

    public static String extractUid(String dn) {
        if (dn == null || dn.isBlank()) {
            return null;
        }
        try {
            for (Rdn rdn : new LdapName(dn).getRdns()) {
                if ("uid".equalsIgnoreCase(rdn.getType())) {
                    Object value = rdn.getValue();
                    return value == null ? null : value.toString();
                }
            }
            return null;
        } catch (InvalidNameException exception) {
            return null;
        }
    }

    public static String toAbsoluteDn(AppLdapProperties ldapProperties, Name relativeDn) {
        String value = relativeDn.toString();
        String suffix = "," + ldapProperties.getBaseDn();
        if (value.endsWith(suffix) || value.equals(ldapProperties.getBaseDn())) {
            return value;
        }
        return value + suffix;
    }

    private static String normalizeOuValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.regionMatches(true, 0, "ou=", 0, 3) ? value.substring(3) : value;
    }
}
