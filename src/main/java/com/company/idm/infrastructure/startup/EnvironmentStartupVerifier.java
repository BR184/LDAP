package com.company.idm.infrastructure.startup;

import com.company.idm.infrastructure.config.AppLdapProperties;
import com.company.idm.infrastructure.config.StartupCheckProperties;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Component;

/**
 * 在真实环境启动时执行数据库和 LDAP 基础连通性校验。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.startup-check", name = "enabled", havingValue = "true")
public class EnvironmentStartupVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentStartupVerifier.class);

    private final DataSource dataSource;
    private final AppLdapProperties ldapProperties;
    private final StartupCheckProperties startupCheckProperties;
    private final org.springframework.beans.factory.ObjectProvider<LdapTemplate> ldapTemplateProvider;

    @Override
    public void run(ApplicationArguments args) {
        verifyDatabase();
        verifyLdapIfNecessary();
        log.info("Runtime environment startup verification completed successfully.");
    }

    private void verifyDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(startupCheckProperties.getDbValidationTimeoutSeconds())) {
                throw new IllegalStateException("Database startup verification failed: connection is invalid.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Database startup verification failed.", exception);
        }
    }

    private void verifyLdapIfNecessary() {
        if (!"spring".equalsIgnoreCase(ldapProperties.getMode())) {
            return;
        }
        LdapTemplate ldapTemplate = ldapTemplateProvider.getIfAvailable();
        if (ldapTemplate == null) {
            throw new IllegalStateException("LDAP startup verification failed: LdapTemplate is not configured.");
        }
        verifySearchBase(ldapTemplate, ldapProperties.getPeopleOu(), "peopleOu");
        verifySearchBase(ldapTemplate, ldapProperties.getGroupsOu(), "groupsOu");
        if (startupCheckProperties.isVerifyPlaceholderUser()) {
            verifyPlaceholderUser(ldapTemplate);
        }
    }

    private void verifySearchBase(LdapTemplate ldapTemplate, String base, String fieldName) {
        try {
            ldapTemplate.search(base, "(objectClass=*)", (AttributesMapper<String>) attributes -> null);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("LDAP startup verification failed: unable to access " + fieldName + ".", exception);
        }
    }

    private void verifyPlaceholderUser(LdapTemplate ldapTemplate) {
        List<String> results = ldapTemplate.search(
            ldapProperties.getPeopleOu(),
            "(uid=" + startupCheckProperties.getPlaceholderUid() + ")",
            (AttributesMapper<String>) attributes -> attributes.get("uid") == null ? null : attributes.get("uid").get().toString()
        );
        if (results.isEmpty()) {
            throw new IllegalStateException("LDAP startup verification failed: placeholder user is missing.");
        }
    }
}
