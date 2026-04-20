package com.company.idm.test;

import com.company.idm.infrastructure.config.AppLdapProperties;
import com.company.idm.infrastructure.ldap.LdapDnHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LdapDnHelperTest {

    private final AppLdapProperties ldapProperties = new AppLdapProperties();

    @Test
    void shouldEscapeSpecialCharactersWhenBuildingUserDn() {
        String dn = LdapDnHelper.buildUserDn(ldapProperties, "+8615850553970");

        assertThat(dn).isEqualTo("uid=\\+8615850553970,ou=people,dc=corp,dc=local");
    }

    @Test
    void shouldExtractRawUsernameFromEscapedDn() {
        String username = LdapDnHelper.extractUid("uid=\\+8615850553970,ou=people,dc=corp,dc=local");

        assertThat(username).isEqualTo("+8615850553970");
    }
}
