package com.company.idm.infrastructure.ldap;

import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.infrastructure.config.AppLdapProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 提供用于本地原型联调的 LDAP 分组桩实现。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ldap", name = "mode", havingValue = "stub", matchIfMissing = true)
public class StubLdapGroupService implements LdapGroupService {

    private final AppLdapProperties ldapProperties;
    private final Map<String, StubGroup> groups = new ConcurrentHashMap<>();

    @Override
    public boolean existsGroup(String groupCode) {
        return groups.containsKey(groupCode);
    }

    @Override
    public String findGroupDn(String groupCode) {
        StubGroup group = groups.get(groupCode);
        return group == null ? null : group.dn;
    }

    @Override
    public String createGroup(String groupCode, String groupName) {
        StubGroup group = groups.computeIfAbsent(groupCode, code -> new StubGroup(code, groupName, buildGroupDn(code, groupName)));
        group.groupName = groupName;
        group.dn = buildGroupDn(groupCode, groupName);
        return group.dn;
    }

    @Override
    public String updateGroup(String groupCode, String groupName) {
        groups.computeIfPresent(groupCode, (code, group) -> {
            group.groupName = groupName;
            group.dn = buildGroupDn(code, groupName);
            return group;
        });
        return groups.containsKey(groupCode) ? groups.get(groupCode).dn : createGroup(groupCode, groupName);
    }

    @Override
    public void deleteGroup(String groupCode) {
        groups.remove(groupCode);
    }

    @Override
    public void addUserToGroup(String username, String groupCode) {
        groups.computeIfAbsent(groupCode, code -> new StubGroup(code, code, buildGroupDn(code, code)))
            .members.add(buildUserDn(username));
    }

    @Override
    public void removeUserFromGroup(String username, String groupCode) {
        StubGroup group = groups.get(groupCode);
        if (group != null) {
            group.members.remove(buildUserDn(username));
        }
    }

    @Override
    public void syncUserGroups(String username, List<String> groupCodes) {
        removeUserFromAllGroups(username);
        if (groupCodes == null) {
            return;
        }
        for (String groupCode : groupCodes) {
            addUserToGroup(username, groupCode);
        }
    }

    @Override
    public void removeUserFromAllGroups(String username) {
        String userDn = buildUserDn(username);
        groups.values().forEach(group -> group.members.remove(userDn));
    }

    /**
     * 返回当前桩环境中的分组快照，供测试断言使用。
     */
    public Map<String, List<String>> snapshotMembers() {
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        groups.forEach((groupCode, group) -> snapshot.put(groupCode, new ArrayList<>(group.members)));
        return snapshot;
    }

    /**
     * 返回当前桩环境中的分组 DN 快照，供测试断言使用。
     */
    public Map<String, String> snapshotDns() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        groups.forEach((groupCode, group) -> snapshot.put(groupCode, group.dn));
        return snapshot;
    }

    private String buildGroupDn(String groupCode, String groupName) {
        return "cn=" + buildGroupCn(groupCode, groupName) + "," + ldapProperties.getGroupsOu() + "," + ldapProperties.getBaseDn();
    }

    private String buildUserDn(String username) {
        return "uid=" + username + "," + ldapProperties.getPeopleOu() + "," + ldapProperties.getBaseDn();
    }

    private String buildGroupCn(String groupCode, String groupName) {
        return groupCode + "_" + groupName;
    }

    private static class StubGroup {
        private final String groupCode;
        private String groupName;
        private String dn;
        private final Set<String> members = new LinkedHashSet<>();

        private StubGroup(String groupCode, String groupName, String dn) {
            this.groupCode = groupCode;
            this.groupName = groupName;
            this.dn = dn;
        }
    }
}
