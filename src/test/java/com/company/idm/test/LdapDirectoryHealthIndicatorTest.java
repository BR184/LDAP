package com.company.idm.test;

import com.company.idm.infrastructure.config.AppLdapProperties;
import com.company.idm.infrastructure.health.LdapDirectoryHealthIndicator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Status;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 验证 LDAP 健康检查组件的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class LdapDirectoryHealthIndicatorTest {

    @Mock
    private LdapTemplate ldapTemplate;

    @Test
    void shouldReportUpWhenLdapSearchesSucceed() {
        AppLdapProperties properties = buildProperties();
        when(ldapTemplate.search(eq("ou=people"), eq("(uid=placeholder)"), org.mockito.ArgumentMatchers.<AttributesMapper<String>>any()))
            .thenReturn(List.of("placeholder"));
        when(ldapTemplate.search(eq("ou=groups"), eq("(objectClass=*)"), org.mockito.ArgumentMatchers.<AttributesMapper<String>>any()))
            .thenReturn(List.of("group"));
        LdapDirectoryHealthIndicator indicator = new LdapDirectoryHealthIndicator(ldapTemplate, properties);

        org.springframework.boot.actuate.health.Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
            .containsEntry("mode", "spring")
            .containsEntry("baseDn", "dc=corp,dc=local")
            .containsEntry("peopleOu", "ou=people")
            .containsEntry("groupsOu", "ou=groups")
            .containsEntry("placeholderUserPresent", true);
    }

    @Test
    void shouldReportDownWhenLdapSearchFails() {
        AppLdapProperties properties = buildProperties();
        when(ldapTemplate.search(eq("ou=people"), eq("(uid=placeholder)"), org.mockito.ArgumentMatchers.<AttributesMapper<String>>any()))
            .thenThrow(new IllegalStateException("ldap down"));
        LdapDirectoryHealthIndicator indicator = new LdapDirectoryHealthIndicator(ldapTemplate, properties);

        org.springframework.boot.actuate.health.Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
            .containsEntry("mode", "spring")
            .containsEntry("baseDn", "dc=corp,dc=local");
    }

    private AppLdapProperties buildProperties() {
        AppLdapProperties properties = new AppLdapProperties();
        properties.setMode("spring");
        properties.setBaseDn("dc=corp,dc=local");
        properties.setPeopleOu("ou=people");
        properties.setGroupsOu("ou=groups");
        return properties;
    }
}
