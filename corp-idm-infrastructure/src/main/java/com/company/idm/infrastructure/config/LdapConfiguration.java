package com.company.idm.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

@Configuration
public class LdapConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.ldap", name = "mode", havingValue = "spring")
    public LdapContextSource ldapContextSource(AppLdapProperties properties) {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(properties.getUrl());
        contextSource.setBase(properties.getBaseDn());
        contextSource.setUserDn(properties.getBindDn());
        contextSource.setPassword(properties.getBindPassword());
        contextSource.afterPropertiesSet();
        return contextSource;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.ldap", name = "mode", havingValue = "spring")
    public LdapTemplate ldapTemplate(LdapContextSource contextSource) {
        return new LdapTemplate(contextSource);
    }
}

