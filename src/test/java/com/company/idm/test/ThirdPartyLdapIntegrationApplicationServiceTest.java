package com.company.idm.test;

import com.company.idm.application.ldap.ThirdPartyLdapCheckStatus;
import com.company.idm.application.ldap.ThirdPartyLdapFrameworkDetail;
import com.company.idm.application.ldap.ThirdPartyLdapIntegrationApplicationService;
import com.company.idm.application.ldap.ThirdPartyLdapPrecheckCommand;
import com.company.idm.application.ldap.ThirdPartyLdapPrecheckReport;
import com.company.idm.application.ldap.ThirdPartyLdapTemplateDetail;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapUserSnapshot;
import com.company.idm.infrastructure.config.AppLdapProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ldap.core.LdapTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 验证第三方 LDAP 通用接入应用服务的核心行为。
 */
@ExtendWith(MockitoExtension.class)
class ThirdPartyLdapIntegrationApplicationServiceTest {

    @Mock
    private LdapDirectoryService ldapDirectoryService;

    @Mock
    private ObjectProvider<LdapTemplate> ldapTemplateProvider;

    @Test
    void shouldBuildFrameworkDetailWithStandardContract() {
        ThirdPartyLdapIntegrationApplicationService service = new ThirdPartyLdapIntegrationApplicationService(
            buildProperties("spring", "ldap://127.0.0.1:389"),
            ldapDirectoryService,
            ldapTemplateProvider
        );

        ThirdPartyLdapFrameworkDetail framework = service.getFramework();

        assertThat(framework.mode()).isEqualTo("spring");
        assertThat(framework.baseDn()).isEqualTo("dc=corp,dc=local");
        assertThat(framework.userBase()).isEqualTo("ou=people,dc=corp,dc=local");
        assertThat(framework.groupBase()).isEqualTo("ou=groups,dc=corp,dc=local");
        assertThat(framework.loginAttr()).isEqualTo("uid");
        assertThat(framework.userFilter()).isEqualTo("(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))");
        assertThat(framework.authorizationMode()).isEqualTo("LOCAL_ONLY");
        assertThat(framework.supportedSystems()).containsExactly("gitlab", "jenkins", "nexus", "zentao");
    }

    @Test
    void shouldBuildGitlabTemplateWithoutExposingBindPassword() {
        ThirdPartyLdapIntegrationApplicationService service = new ThirdPartyLdapIntegrationApplicationService(
            buildProperties("spring", "ldaps://ldap.corp.local:636"),
            ldapDirectoryService,
            ldapTemplateProvider
        );

        ThirdPartyLdapTemplateDetail template = service.getTemplate("gitlab");

        assertThat(template.systemCode()).isEqualTo("gitlab");
        assertThat(template.settings())
            .containsEntry("host", "ldap.corp.local")
            .containsEntry("port", "636")
            .containsEntry("base_dn", "dc=corp,dc=local")
            .containsEntry("user_base", "ou=people,dc=corp,dc=local")
            .containsEntry("user_filter", "(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))")
            .containsEntry("bind_password", "${LDAP_BIND_PASSWORD}")
            .containsEntry("encryption", "simple_tls");
        assertThat(template.fieldMappings())
            .containsEntry("uid", "uid")
            .containsEntry("name", "cn")
            .containsEntry("email", "mail");
        assertThat(template.notes()).anyMatch(item -> item.contains("GitLab"));
    }

    @Test
    void shouldPassStubModePrecheckForEnabledAndDisabledUsers() {
        when(ldapDirectoryService.existsByUid("admin")).thenReturn(true);
        when(ldapDirectoryService.findUserSnapshot("admin")).thenReturn(buildSnapshot("admin", "ENABLED"));
        when(ldapDirectoryService.findUserSnapshot("disabled-user")).thenReturn(buildSnapshot("disabled-user", "DISABLED"));

        ThirdPartyLdapIntegrationApplicationService service = new ThirdPartyLdapIntegrationApplicationService(
            buildProperties("stub", null),
            ldapDirectoryService,
            ldapTemplateProvider
        );

        ThirdPartyLdapPrecheckReport report = service.precheck(new ThirdPartyLdapPrecheckCommand("gitlab", "admin", "disabled-user"));

        assertThat(report.systemCode()).isEqualTo("gitlab");
        assertThat(report.mode()).isEqualTo("stub");
        assertThat(report.overallStatus()).isEqualTo(ThirdPartyLdapCheckStatus.PASS);
        assertThat(report.items()).filteredOn(item -> "ENABLED_USER_FILTER_MATCH".equals(item.code()))
            .singleElement()
            .satisfies(item -> assertThat(item.status()).isEqualTo(ThirdPartyLdapCheckStatus.PASS));
        assertThat(report.items()).filteredOn(item -> "DISABLED_USER_FILTER_BLOCK".equals(item.code()))
            .singleElement()
            .satisfies(item -> assertThat(item.status()).isEqualTo(ThirdPartyLdapCheckStatus.PASS));
    }

    private AppLdapProperties buildProperties(String mode, String url) {
        AppLdapProperties properties = new AppLdapProperties();
        properties.setMode(mode);
        properties.setUrl(url);
        properties.setBaseDn("dc=corp,dc=local");
        properties.setPeopleOu("ou=people");
        properties.setGroupsOu("ou=groups");
        properties.setBindDn("cn=readonly,dc=corp,dc=local");
        properties.setBindPassword("secret-should-not-leak");
        return properties;
    }

    private LdapUserSnapshot buildSnapshot(String username, String status) {
        return LdapUserSnapshot.builder()
            .username(username)
            .realName(username)
            .status(status)
            .dn("uid=" + username + ",ou=people,dc=corp,dc=local")
            .build();
    }
}
