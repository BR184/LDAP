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

/**
 * 提供第三方 LDAP 通用接入框架的模板与预检能力。
 */
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
                settings.put("encryption", connectionView.encryption());

                fieldMappings.put("uid", "uid");
                fieldMappings.put("name", "cn");
                fieldMappings.put("email", "mail");

                notes.add("GitLab 只使用 LDAP 做认证，项目与组权限继续由 GitLab 本地维护。");
                notes.add("GitLab 会自动按 uid=%{username} 拼接登录条件，模板中的 user_filter 只保留 objectClass 与 employeeType 附加限制。");
                notes.add("允许首登自动创建本地用户，但登录标识必须固定为 uid。");
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

                notes.add("Jenkins 常见配置是只按 uid 搜索，必须保留 employeeType=ENABLED 过滤条件。");
                notes.add("Jenkins 权限体系继续由本地矩阵授权或角色策略维护。");
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

                notes.add("Nexus 需要单独确认 LDAP Realm 的启用顺序与缓存刷新策略。");
                notes.add("LDAP 认证成功后，仓库与角色权限仍由 Nexus 本地角色模型决定。");
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

                notes.add("禅道模板当前作为占位模板使用，最终字段名仍需按目标版本官方手册确认。");
                notes.add("不允许脱离统一目录契约单独修改登录字段或禁用过滤条件。");
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
        String enabledUsername = requireUsername(command.enabledUsername());
        String disabledUsername = blankToNull(command.disabledUsername());
        String systemCode = blankToNull(command.systemCode());
        if (systemCode != null) {
            systemCode = ThirdPartyLdapSystemType.fromCode(systemCode).getCode();
        }

        List<ThirdPartyLdapPrecheckItem> items = new ArrayList<>();
        items.add(new ThirdPartyLdapPrecheckItem(
            "LOGIN_ATTR_FIXED",
            "登录字段固定",
            ThirdPartyLdapCheckStatus.PASS,
            "第三方系统登录字段必须固定为 uid"
        ));

        if (isSpringMode()) {
            items.add(checkSearchBase("PEOPLE_OU_ACCESS", "用户目录可访问", ldapProperties.getPeopleOu()));
            items.add(checkSearchBase("GROUPS_OU_ACCESS", "分组目录可访问", ldapProperties.getGroupsOu()));
            items.add(checkEnabledUserUniqueInSpring(enabledUsername));
            items.add(checkEnabledUserFilterMatchInSpring(enabledUsername));
            items.add(checkDisabledUserFilterBlockInSpring(disabledUsername));
        } else {
            items.add(new ThirdPartyLdapPrecheckItem(
                "PEOPLE_OU_ACCESS",
                "用户目录可访问",
                ThirdPartyLdapCheckStatus.PASS,
                "当前为 stub 模式，用户目录按内存目录模拟"
            ));
            items.add(new ThirdPartyLdapPrecheckItem(
                "GROUPS_OU_ACCESS",
                "分组目录可访问",
                ThirdPartyLdapCheckStatus.PASS,
                "当前为 stub 模式，分组目录按内存目录模拟"
            ));
            items.add(checkEnabledUserUniqueInStub(enabledUsername));
            items.add(checkEnabledUserFilterMatchInStub(enabledUsername));
            items.add(checkDisabledUserFilterBlockInStub(disabledUsername));
        }

        ThirdPartyLdapCheckStatus overallStatus = items.stream().anyMatch(item -> item.status() == ThirdPartyLdapCheckStatus.FAIL)
            ? ThirdPartyLdapCheckStatus.FAIL
            : ThirdPartyLdapCheckStatus.PASS;
        return new ThirdPartyLdapPrecheckReport(systemCode, safeMode(), STANDARD_FILTER, overallStatus, items);
    }

    private ThirdPartyLdapPrecheckItem checkSearchBase(String code, String name, String base) {
        Optional<LdapTemplate> ldapTemplate = currentLdapTemplate();
        if (ldapTemplate.isEmpty()) {
            return new ThirdPartyLdapPrecheckItem(code, name, ThirdPartyLdapCheckStatus.FAIL, "LdapTemplate 未配置，无法执行真实目录预检");
        }
        try {
            ldapTemplate.get().search(base, "(objectClass=*)", (AttributesMapper<String>) attributes -> null);
            return new ThirdPartyLdapPrecheckItem(code, name, ThirdPartyLdapCheckStatus.PASS, "已访问 " + base);
        } catch (RuntimeException exception) {
            return new ThirdPartyLdapPrecheckItem(code, name, ThirdPartyLdapCheckStatus.FAIL, "访问 " + base + " 失败: " + exception.getMessage());
        }
    }

    private ThirdPartyLdapPrecheckItem checkEnabledUserUniqueInSpring(String username) {
        Optional<LdapTemplate> ldapTemplate = currentLdapTemplate();
        if (ldapTemplate.isEmpty()) {
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_UNIQUE", "启用用户唯一性", ThirdPartyLdapCheckStatus.FAIL, "LdapTemplate 未配置");
        }
        String filter = "(uid=" + LdapEncoder.filterEncode(username) + ")";
        try {
            int matchCount = ldapTemplate.get().search(
                ldapProperties.getPeopleOu(),
                filter,
                (AttributesMapper<String>) attributes -> attributes.get("uid") == null ? null : attributes.get("uid").get().toString()
            ).size();
            if (matchCount == 1) {
                return new ThirdPartyLdapPrecheckItem("ENABLED_USER_UNIQUE", "启用用户唯一性", ThirdPartyLdapCheckStatus.PASS, username + " 在 LDAP 中唯一命中");
            }
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_UNIQUE", "启用用户唯一性", ThirdPartyLdapCheckStatus.FAIL, username + " 命中数量为 " + matchCount);
        } catch (RuntimeException exception) {
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_UNIQUE", "启用用户唯一性", ThirdPartyLdapCheckStatus.FAIL, "查询失败: " + exception.getMessage());
        }
    }

    private ThirdPartyLdapPrecheckItem checkEnabledUserFilterMatchInSpring(String username) {
        Optional<LdapTemplate> ldapTemplate = currentLdapTemplate();
        if (ldapTemplate.isEmpty()) {
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_FILTER_MATCH", "启用用户过滤命中", ThirdPartyLdapCheckStatus.FAIL, "LdapTemplate 未配置");
        }
        String filter = buildConcreteStandardFilter(username);
        try {
            int matchCount = ldapTemplate.get().search(
                ldapProperties.getPeopleOu(),
                filter,
                (AttributesMapper<String>) attributes -> attributes.get("uid") == null ? null : attributes.get("uid").get().toString()
            ).size();
            if (matchCount == 1) {
                return new ThirdPartyLdapPrecheckItem("ENABLED_USER_FILTER_MATCH", "启用用户过滤命中", ThirdPartyLdapCheckStatus.PASS, "统一过滤器可命中启用用户 " + username);
            }
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_FILTER_MATCH", "启用用户过滤命中", ThirdPartyLdapCheckStatus.FAIL, "统一过滤器未命中启用用户 " + username);
        } catch (RuntimeException exception) {
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_FILTER_MATCH", "启用用户过滤命中", ThirdPartyLdapCheckStatus.FAIL, "查询失败: " + exception.getMessage());
        }
    }

    private ThirdPartyLdapPrecheckItem checkDisabledUserFilterBlockInSpring(String username) {
        if (username == null) {
            return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "禁用用户过滤拦截", ThirdPartyLdapCheckStatus.SKIPPED, "未提供禁用用户样本");
        }
        Optional<LdapTemplate> ldapTemplate = currentLdapTemplate();
        if (ldapTemplate.isEmpty()) {
            return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "禁用用户过滤拦截", ThirdPartyLdapCheckStatus.FAIL, "LdapTemplate 未配置");
        }
        try {
            int rawCount = ldapTemplate.get().search(
                ldapProperties.getPeopleOu(),
                "(uid=" + LdapEncoder.filterEncode(username) + ")",
                (AttributesMapper<String>) attributes -> attributes.get("uid") == null ? null : attributes.get("uid").get().toString()
            ).size();
            if (rawCount == 0) {
                return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "禁用用户过滤拦截", ThirdPartyLdapCheckStatus.FAIL, "禁用用户样本不存在 " + username);
            }
            int enabledFilterCount = ldapTemplate.get().search(
                ldapProperties.getPeopleOu(),
                buildConcreteStandardFilter(username),
                (AttributesMapper<String>) attributes -> attributes.get("uid") == null ? null : attributes.get("uid").get().toString()
            ).size();
            if (enabledFilterCount == 0) {
                return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "禁用用户过滤拦截", ThirdPartyLdapCheckStatus.PASS, "统一过滤器已拦截禁用用户 " + username);
            }
            return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "禁用用户过滤拦截", ThirdPartyLdapCheckStatus.FAIL, "统一过滤器仍可命中禁用用户 " + username);
        } catch (RuntimeException exception) {
            return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "禁用用户过滤拦截", ThirdPartyLdapCheckStatus.FAIL, "查询失败: " + exception.getMessage());
        }
    }

    private ThirdPartyLdapPrecheckItem checkEnabledUserUniqueInStub(String username) {
        return ldapDirectoryService.existsByUid(username)
            ? new ThirdPartyLdapPrecheckItem("ENABLED_USER_UNIQUE", "启用用户唯一性", ThirdPartyLdapCheckStatus.PASS, username + " 在 stub 目录中存在")
            : new ThirdPartyLdapPrecheckItem("ENABLED_USER_UNIQUE", "启用用户唯一性", ThirdPartyLdapCheckStatus.FAIL, username + " 不存在于 stub 目录");
    }

    private ThirdPartyLdapPrecheckItem checkEnabledUserFilterMatchInStub(String username) {
        LdapUserSnapshot snapshot = ldapDirectoryService.findUserSnapshot(username);
        if (snapshot == null) {
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_FILTER_MATCH", "启用用户过滤命中", ThirdPartyLdapCheckStatus.FAIL, "启用用户样本不存在 " + username);
        }
        if ("ENABLED".equalsIgnoreCase(snapshot.getStatus())) {
            return new ThirdPartyLdapPrecheckItem("ENABLED_USER_FILTER_MATCH", "启用用户过滤命中", ThirdPartyLdapCheckStatus.PASS, "统一过滤器可命中启用用户 " + username);
        }
        return new ThirdPartyLdapPrecheckItem("ENABLED_USER_FILTER_MATCH", "启用用户过滤命中", ThirdPartyLdapCheckStatus.FAIL, username + " 当前不是启用状态");
    }

    private ThirdPartyLdapPrecheckItem checkDisabledUserFilterBlockInStub(String username) {
        if (username == null) {
            return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "禁用用户过滤拦截", ThirdPartyLdapCheckStatus.SKIPPED, "未提供禁用用户样本");
        }
        LdapUserSnapshot snapshot = ldapDirectoryService.findUserSnapshot(username);
        if (snapshot == null) {
            return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "禁用用户过滤拦截", ThirdPartyLdapCheckStatus.FAIL, "禁用用户样本不存在 " + username);
        }
        if ("DISABLED".equalsIgnoreCase(snapshot.getStatus())) {
            return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "禁用用户过滤拦截", ThirdPartyLdapCheckStatus.PASS, "统一过滤器会拦截禁用用户 " + username);
        }
        return new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "禁用用户过滤拦截", ThirdPartyLdapCheckStatus.FAIL, username + " 当前不是禁用状态");
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
            throw new BizException("LDAP_PRECHECK_USER_REQUIRED", "启用用户样本不能为空");
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String buildConcreteStandardFilter(String username) {
        return "(&(objectClass=inetOrgPerson)(uid=" + LdapEncoder.filterEncode(username) + ")(employeeType=ENABLED))";
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
