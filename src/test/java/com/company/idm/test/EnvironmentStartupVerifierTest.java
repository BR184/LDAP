package com.company.idm.test;

import com.company.idm.infrastructure.config.AppLdapProperties;
import com.company.idm.infrastructure.config.StartupCheckProperties;
import com.company.idm.infrastructure.startup.EnvironmentStartupVerifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证真实环境启动校验器的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentStartupVerifierTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private LdapTemplate ldapTemplate;

    @Mock
    private ObjectProvider<LdapTemplate> ldapTemplateProvider;

    @Test
    void shouldVerifyDatabaseOnlyWhenLdapModeIsStub() throws SQLException {
        AppLdapProperties ldapProperties = new AppLdapProperties();
        ldapProperties.setMode("stub");
        StartupCheckProperties startupCheckProperties = new StartupCheckProperties();
        startupCheckProperties.setEnabled(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        EnvironmentStartupVerifier verifier = new EnvironmentStartupVerifier(
            dataSource,
            ldapProperties,
            startupCheckProperties,
            ldapTemplateProvider
        );

        verifier.run(new DefaultApplicationArguments(new String[0]));

        verify(dataSource).getConnection();
    }

    @Test
    void shouldFailWhenPlaceholderUserMissingInSpringMode() throws SQLException {
        AppLdapProperties ldapProperties = new AppLdapProperties();
        ldapProperties.setMode("spring");
        ldapProperties.setPeopleOu("ou=people");
        ldapProperties.setGroupsOu("ou=groups");
        StartupCheckProperties startupCheckProperties = new StartupCheckProperties();
        startupCheckProperties.setEnabled(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        when(ldapTemplateProvider.getIfAvailable()).thenReturn(ldapTemplate);
        when(ldapTemplate.search(eq("ou=people"), eq("(objectClass=*)"), org.mockito.ArgumentMatchers.<AttributesMapper<String>>any()))
            .thenReturn(List.of());
        when(ldapTemplate.search(eq("ou=groups"), eq("(objectClass=*)"), org.mockito.ArgumentMatchers.<AttributesMapper<String>>any()))
            .thenReturn(List.of());
        when(ldapTemplate.search(eq("ou=people"), eq("(uid=placeholder)"), org.mockito.ArgumentMatchers.<AttributesMapper<String>>any()))
            .thenReturn(List.of());

        EnvironmentStartupVerifier verifier = new EnvironmentStartupVerifier(
            dataSource,
            ldapProperties,
            startupCheckProperties,
            ldapTemplateProvider
        );

        assertThatThrownBy(() -> verifier.run(new DefaultApplicationArguments(new String[0])))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("LDAP startup verification failed: placeholder user is missing.");
    }
}
