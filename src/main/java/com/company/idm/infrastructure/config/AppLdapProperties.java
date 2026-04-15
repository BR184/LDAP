package com.company.idm.infrastructure.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 LDAP 相关的应用配置项。
 */
@ConfigurationProperties(prefix = "app.ldap")
public class AppLdapProperties {

    private String mode = "stub";
    private String url;
    private String baseDn = "dc=corp,dc=local";
    private String bindDn;
    private String bindPassword;
    private String peopleOu = "ou=people";
    private Map<String, String> stubUsers = new HashMap<>();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBaseDn() {
        return baseDn;
    }

    public void setBaseDn(String baseDn) {
        this.baseDn = baseDn;
    }

    public String getBindDn() {
        return bindDn;
    }

    public void setBindDn(String bindDn) {
        this.bindDn = bindDn;
    }

    public String getBindPassword() {
        return bindPassword;
    }

    public void setBindPassword(String bindPassword) {
        this.bindPassword = bindPassword;
    }

    public String getPeopleOu() {
        return peopleOu;
    }

    public void setPeopleOu(String peopleOu) {
        this.peopleOu = peopleOu;
    }

    public Map<String, String> getStubUsers() {
        return stubUsers;
    }

    public void setStubUsers(Map<String, String> stubUsers) {
        this.stubUsers = stubUsers;
    }
}

