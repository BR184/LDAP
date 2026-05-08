package com.company.idm.application.ldap;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapUserSnapshot;
import com.company.idm.infrastructure.config.AppLdapProperties;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.support.LdapEncoder;
import org.springframework.stereotype.Service;

@Service
public class ThirdPartyLdapIntegrationApplicationService {

    private static final String LOGIN_ATTR = "uid";
    private static final String STANDARD_FILTER = "(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))";
    private static final String AUTHORIZATION_MODE = "LOCAL_ONLY";
    private static final String PASSWORD_PLACEHOLDER = "${LDAP_BIND_PASSWORD}";

    private final AppLdapProperties ldapProperties;
    private final LdapDirectoryService ldapDirectoryService;
    private final ObjectProvider<LdapTemplate> ldapTemplateProvider;

    public ThirdPartyLdapIntegrationApplicationService(
        AppLdapProperties ldapProperties,
        LdapDirectoryService ldapDirectoryService,
        ObjectProvider<LdapTemplate> ldapTemplateProvider
    ) {
        this.ldapProperties = ldapProperties;
        this.ldapDirectoryService = ldapDirectoryService;
        this.ldapTemplateProvider = ldapTemplateProvider;
    }

    public ThirdPartyLdapFrameworkDetail getFramework() {
        return new ThirdPartyLdapFrameworkDetail(
            safeMode(),
            ldapProperties.getBaseDn(),
            buildAbsoluteDn(ldapProperties.getPeopleOu()),
            buildAbsoluteDn(ldapProperties.getGroupsOu()),
            LOGIN_ATTR,
            STANDARD_FILTER,
            AUTHORIZATION_MODE,
            List.of("gitlab", "jenkins", "nexus", "zentao")
        );
    }

    public ThirdPartyLdapTemplateDetail getTemplate(String systemCode) {
        ThirdPartyLdapSystemType systemType = ThirdPartyLdapSystemType.fromCode(systemCode);
        LdapConnectionView connectionView = resolveConnectionView();
        LinkedHashMap<String, String> settings = new LinkedHashMap<>();
        LinkedHashMap<String, String> fieldMappings = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();

        switch (systemType) {
            case GITLAB -> {
                settings.put("host", connectionView.host());
                settings.put("port", connectionView.port());
                settings.put("bind_dn", safeBindDn());
                settings.put("bind_password", PASSWORD_PLACEHOLDER);
                settings.put("base_dn", ldapProperties.getBaseDn());
                settings.put("user_base", buildAbsoluteDn(ldapProperties.getPeopleOu()));
                settings.put("user_filter", systemType.buildTemplateUserFilter());
                settings.put("uid", "uid");
                settings.put("encryption", connectionView.encryption());

                fieldMappings.put("uid", "uid");
                fieldMappings.put("name", "cn");
                fieldMappings.put("email", "mail");

                notes.add("GitLab authenticates against LDAP only.");
                notes.add("Login attribute must be uid, and uid now stores the platform userId.");
                notes.add("Keep the enabled-user filter on employeeType.");
            }
            case JENKINS -> {
                settings.put("ldap_server", connectionView.url());
                settings.put("root_dn", ldapProperties.getBaseDn());
                settings.put("user_search_base", ldapProperties.getPeopleOu());
                settings.put("user_search_filter", systemType.buildUserFilter());
                settings.put("manager_dn", safeBindDn());
                settings.put("manager_password", PASSWORD_PLACEHOLDER);

                fieldMappings.put("display_name", "cn");
                fieldMappings.put("mail", "mail");
                fieldMappings.put("login_attr", LOGIN_ATTR);

                notes.add("Jenkins should log in with uid.");
            }
            case NEXUS -> {
                settings.put("connection_url", connectionView.url());
                settings.put("search_base", ldapProperties.getBaseDn());
                settings.put("authentication_method", "simple");
                settings.put("bind_dn", safeBindDn());
                settings.put("bind_password", PASSWORD_PLACEHOLDER);
                settings.put("user_base_dn", buildAbsoluteDn(ldapProperties.getPeopleOu()));
                settings.put("user_filter", systemType.buildUserFilter());

                fieldMappings.put("user_id", "uid");
                fieldMappings.put("real_name", "cn");
                fieldMappings.put("email", "mail");

                notes.add("Nexus should map the login identity to uid.");
            }
            case ZENTAO -> {
                settings.put("ldap_server", connectionView.host() + ":" + connectionView.port());
                settings.put("base_dn", ldapProperties.getBaseDn());
                settings.put("bind_dn", safeBindDn());
                settings.put("bind_password", PASSWORD_PLACEHOLDER);
                settings.put("user_search_base", buildAbsoluteDn(ldapProperties.getPeopleOu()));
                settings.put("user_search_filter", systemType.buildUserFilter());

                fieldMappings.put("username", "uid");
                fieldMappings.put("display_name", "cn");
                fieldMappings.put("mail", "mail");

                notes.add("Zentao template keeps uid as the login identity.");
            }
        }

        return new ThirdPartyLdapTemplateDetail(
            systemType.getCode(),
            systemType.getDisplayName(),
            systemType.getDocumentPath(),
            settings,
            fieldMappings,
            notes
        );
    }

    public ThirdPartyLdapPrecheckReport precheck(ThirdPartyLdapPrecheckCommand command) {
        String enabledUserId = requireUsername(command.enabledUsername());
        String disabledUserId = blankToNull(command.disabledUsername());
        String systemCode = blankToNull(command.systemCode());
        if (systemCode != null) {
            systemCode = ThirdPartyLdapSystemType.fromCode(systemCode).getCode();
        }

        List<ThirdPartyLdapPrecheckItem> items = new ArrayList<>();
        items.add(new ThirdPartyLdapPrecheckItem(
            "LOGIN_ATTR_FIXED",
            "Login Attribute",
            ThirdPartyLdapCheckStatus.PASS,
            "Third-party systems must use uid as the login attribute."
        ));

        if (isSpringMode()) {
            items.add(checkSearchBase("PEOPLE_OU_ACCESS", "People OU", ldapProperties.getPeopleOu()));
            items.add(checkSearchBase("GROUPS_OU_ACCESS", "Groups OU", ldapProperties.getGroupsOu()));
            items.add(checkEnabledUserUniqueInSpring(enabledUserId));
            items.add(checkEnabledUserFilterMatchInSpring(enabledUserId));
            items.add(checkDisabledUserFilterBlockInSpring(disabledUserId));
        } else {
            items.add(new ThirdPartyLdapPrecheckItem(
                "PEOPLE_OU_ACCESS",
                "People OU",
                ThirdPartyLdapCheckStatus.PASS,
                "Stub mode uses in-memory user entries."
            ));
            items.add(new ThirdPartyLdapPrecheckItem(
                "GROUPS_OU_ACCESS",
                "Groups OU",
                ThirdPartyLdapCheckStatus.PASS,
                "Stub mode uses in-memory group entries."
            ));
            items.add(checkEnabledUserUniqueInStub(enabledUserId));
            items.add(checkEnabledUserFilterMatchInStub(enabledUserId));
            items.add(checkDisabledUserFilterBlockInStub(disabledUserId));
        }

        ThirdPartyLdapCheckStatus overallStatus = items.stream().anyMatch(item -> item.status() == ThirdPartyLdapCheckStatus.FAIL)
            ? ThirdPartyLdapCheckStatus.FAIL
            : ThirdPartyLdapCheckStatus.PASS;
        return new ThirdPartyLdapPrecheckReport(systemCode, safeMode(), STANDARD_FILTER, overallStatus, items);
    }

    private ThirdPartyLdapPrecheckItem checkSearchBase(String code, String name, String base) {
        Optional<LdapTemplate> ldapTemplate = currentLdapTemplate();
        if (ldapTemplate.isEmpty()) {
            return new ThirdPartyLdapPrecheckItem(code, name, ThirdPartyLdapCheckStatus.FAIL, "LdapTemplate is unavailable.");
        }
        try {
            ldapTemplate.get().search(base, "(objectClass=*)", (AttributesMapper<String>) attributes -> null);
            return new ThirdPartyLdapPrecheckItem(code, name, ThirdPartyLdapCheckStatus.PASS, "Accessible: " + base);
        } catch (RuntimeException exception) {
            return new ThirdPartyLdapPrecheckItem(code, name, ThirdPartyLdapCheckStatus.FAIL, "Access failed: " + exception.getMessage());
        }
    }

    private ThirdPartyLdapPrecheckItem checkEnabledUserUniqueInSpring(String userId) {
        Optional<LdapTemplate> ldapTemplate = currentLdapTemplate();
        if (ldapTemplate.isEmpty()) {
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_UNIQUE", "Enabled User Unique", ThirdPartyLdapCheckStatus.FAIL, "LdapTemplate is unavailable.");
        }
        String filter = "(uid=" + LdapEncoder.filterEncode(userId) + ")";
        try {
            int matchCount = ldapTemplate.get().search(
                ldapProperties.getPeopleOu(),
                filter,
                (AttributesMapper<String>) attributes -> attributes.get("uid") == null ? null : attributes.get("uid").get().toString()
            ).size();
            if (matchCount == 1) {
                return new ThirdPartyLdapPrecheckItem("ENABLED_USER_UNIQUE", "Enabled User Unique", ThirdPartyLdapCheckStatus.PASS, "Matched exactly one uid: " + userId);
            }
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_UNIQUE", "Enabled User Unique", ThirdPartyLdapCheckStatus.FAIL, "Match count = " + matchCount);
        } catch (RuntimeException exception) {
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_UNIQUE", "Enabled User Unique", ThirdPartyLdapCheckStatus.FAIL, "Query failed: " + exception.getMessage());
        }
    }

    private ThirdPartyLdapPrecheckItem checkEnabledUserFilterMatchInSpring(String userId) {
        Optional<LdapTemplate> ldapTemplate = currentLdapTemplate();
        if (ldapTemplate.isEmpty()) {
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_FILTER_MATCH", "Enabled User Filter", ThirdPartyLdapCheckStatus.FAIL, "LdapTemplate is unavailable.");
        }
        String filter = buildConcreteStandardFilter(userId);
        try {
            int matchCount = ldapTemplate.get().search(
                ldapProperties.getPeopleOu(),
                filter,
                (AttributesMapper<String>) attributes -> attributes.get("uid") == null ? null : attributes.get("uid").get().toString()
            ).size();
            if (matchCount == 1) {
                return new ThirdPartyLdapPrecheckItem("ENABLED_USER_FILTER_MATCH", "Enabled User Filter", ThirdPartyLdapCheckStatus.PASS, "Standard filter matched user " + userId);
            }
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_FILTER_MATCH", "Enabled User Filter", ThirdPartyLdapCheckStatus.FAIL, "Standard filter did not match user " + userId);
        } catch (RuntimeException exception) {
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_FILTER_MATCH", "Enabled User Filter", ThirdPartyLdapCheckStatus.FAIL, "Query failed: " + exception.getMessage());
        }
    }

    private ThirdPartyLdapPrecheckItem checkDisabledUserFilterBlockInSpring(String userId) {
        if (userId == null) {
            return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "Disabled User Block", ThirdPartyLdapCheckStatus.SKIPPED, "No disabled user sample provided.");
        }
        Optional<LdapTemplate> ldapTemplate = currentLdapTemplate();
        if (ldapTemplate.isEmpty()) {
            return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "Disabled User Block", ThirdPartyLdapCheckStatus.FAIL, "LdapTemplate is unavailable.");
        }
        try {
            int rawCount = ldapTemplate.get().search(
                ldapProperties.getPeopleOu(),
                "(uid=" + LdapEncoder.filterEncode(userId) + ")",
                (AttributesMapper<String>) attributes -> attributes.get("uid") == null ? null : attributes.get("uid").get().toString()
            ).size();
            if (rawCount == 0) {
                return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "Disabled User Block", ThirdPartyLdapCheckStatus.FAIL, "Disabled user sample does not exist: " + userId);
            }
            int enabledFilterCount = ldapTemplate.get().search(
                ldapProperties.getPeopleOu(),
                buildConcreteStandardFilter(userId),
                (AttributesMapper<String>) attributes -> attributes.get("uid") == null ? null : attributes.get("uid").get().toString()
            ).size();
            if (enabledFilterCount == 0) {
                return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "Disabled User Block", ThirdPartyLdapCheckStatus.PASS, "Standard filter blocks disabled user " + userId);
            }
            return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "Disabled User Block", ThirdPartyLdapCheckStatus.FAIL, "Standard filter still matches disabled user " + userId);
        } catch (RuntimeException exception) {
            return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "Disabled User Block", ThirdPartyLdapCheckStatus.FAIL, "Query failed: " + exception.getMessage());
        }
    }

    private ThirdPartyLdapPrecheckItem checkEnabledUserUniqueInStub(String userId) {
        return ldapDirectoryService.existsByUid(userId)
            ? new ThirdPartyLdapPrecheckItem("ENABLED_USER_UNIQUE", "Enabled User Unique", ThirdPartyLdapCheckStatus.PASS, "User exists in stub: " + userId)
            : new ThirdPartyLdapPrecheckItem("ENABLED_USER_UNIQUE", "Enabled User Unique", ThirdPartyLdapCheckStatus.FAIL, "User not found in stub: " + userId);
    }

    private ThirdPartyLdapPrecheckItem checkEnabledUserFilterMatchInStub(String userId) {
        LdapUserSnapshot snapshot = ldapDirectoryService.findUserSnapshot(userId);
        if (snapshot == null) {
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_FILTER_MATCH", "Enabled User Filter", ThirdPartyLdapCheckStatus.FAIL, "Enabled user sample does not exist: " + userId);
        }
        if ("ENABLED".equalsIgnoreCase(snapshot.getStatus())) {
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_FILTER_MATCH", "Enabled User Filter", ThirdPartyLdapCheckStatus.PASS, "User is enabled: " + userId);
        }
        return new ThirdPartyLdapPrecheckItem("ENABLED_USER_FILTER_MATCH", "Enabled User Filter", ThirdPartyLdapCheckStatus.FAIL, "User is not enabled: " + userId);
    }

    private ThirdPartyLdapPrecheckItem checkDisabledUserFilterBlockInStub(String userId) {
        if (userId == null) {
            return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "Disabled User Block", ThirdPartyLdapCheckStatus.SKIPPED, "No disabled user sample provided.");
        }
        LdapUserSnapshot snapshot = ldapDirectoryService.findUserSnapshot(userId);
        if (snapshot == null) {
            return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "Disabled User Block", ThirdPartyLdapCheckStatus.FAIL, "Disabled user sample does not exist: " + userId);
        }
        if ("DISABLED".equalsIgnoreCase(snapshot.getStatus())) {
            return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "Disabled User Block", ThirdPartyLdapCheckStatus.PASS, "User is disabled: " + userId);
        }
        return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "Disabled User Block", ThirdPartyLdapCheckStatus.FAIL, "User is not disabled: " + userId);
    }

    private Optional<LdapTemplate> currentLdapTemplate() {
        return Optional.ofNullable(ldapTemplateProvider.getIfAvailable());
    }

    private boolean isSpringMode() {
        return "spring".equalsIgnoreCase(safeMode());
    }

    private String safeMode() {
        return blankToNull(ldapProperties.getMode()) == null ? "stub" : ldapProperties.getMode().toLowerCase(Locale.ROOT);
    }

    private String requireUsername(String username) {
        String value = blankToNull(username);
        if (value == null) {
            throw new BizException("LDAP_PRECHECK_USER_REQUIRED", "Enabled user sample is required.");
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String buildConcreteStandardFilter(String userId) {
        return "(&(objectClass=inetOrgPerson)(uid=" + LdapEncoder.filterEncode(userId) + ")(employeeType=ENABLED))";
    }

    private String buildAbsoluteDn(String relativeDn) {
        return relativeDn + "," + ldapProperties.getBaseDn();
    }

    private String safeBindDn() {
        return blankToNull(ldapProperties.getBindDn()) == null ? "<bind_dn>" : ldapProperties.getBindDn();
    }

    private LdapConnectionView resolveConnectionView() {
        String rawUrl = blankToNull(ldapProperties.getUrl());
        if (rawUrl == null) {
            return new LdapConnectionView("<ldap-host>", "389", "ldap://<ldap-host>:389", "plain");
        }
        try {
            URI uri = URI.create(rawUrl);
            String scheme = blankToNull(uri.getScheme()) == null ? "ldap" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = blankToNull(uri.getHost()) == null ? "<ldap-host>" : uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : ("ldaps".equals(scheme) ? 636 : 389);
            String encryption = "ldaps".equals(scheme) ? "simple_tls" : "plain";
            return new LdapConnectionView(host, String.valueOf(port), scheme + "://" + host + ":" + port, encryption);
        } catch (IllegalArgumentException exception) {
            return new LdapConnectionView("<ldap-host>", "389", rawUrl, "plain");
        }
    }

    private record LdapConnectionView(
        String host,
        String port,
        String url,
        String encryption
    ) {
    }
}
