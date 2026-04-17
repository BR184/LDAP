package com.company.idm.infrastructure.health;

import com.company.idm.infrastructure.config.AppLdapProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Component;

/**
 * 提供 OpenLDAP 目录健康检查能力。
 */
@Component("ldapDirectory")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ldap", name = "mode", havingValue = "spring")
public class LdapDirectoryHealthIndicator implements HealthIndicator {

    private final LdapTemplate ldapTemplate;
    private final AppLdapProperties ldapProperties;

    @Override
    public Health health() {
        try {
            List<String> placeholder = ldapTemplate.search(
                ldapProperties.getPeopleOu(),
                "(uid=placeholder)",
                (AttributesMapper<String>) attributes -> attributes.get("uid") == null ? null : attributes.get("uid").get().toString()
            );
            ldapTemplate.search(ldapProperties.getGroupsOu(), "(objectClass=*)", (AttributesMapper<String>) attributes -> null);
            return Health.up()
                .withDetail("mode", ldapProperties.getMode())
                .withDetail("baseDn", ldapProperties.getBaseDn())
                .withDetail("peopleOu", ldapProperties.getPeopleOu())
                .withDetail("groupsOu", ldapProperties.getGroupsOu())
                .withDetail("placeholderUserPresent", !placeholder.isEmpty())
                .build();
        } catch (RuntimeException exception) {
            return Health.down(exception)
                .withDetail("mode", ldapProperties.getMode())
                .withDetail("baseDn", ldapProperties.getBaseDn())
                .build();
        }
    }
}
