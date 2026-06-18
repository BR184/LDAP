package com.company.idm.domain.ldap;

import java.util.List;

/**
 * 定义 LDAP 分组目录的操作接口。
 */
public interface LdapGroupService {

    boolean existsGroup(String groupCode);

    String findGroupDn(String groupCode);

    LdapGroupSnapshot findGroupSnapshot(String groupCode);

    List<String> listAllGroupCodes();

    List<String> listUserGroups(String username);

    String createGroup(String groupCode, String groupName);

    String updateGroup(String groupCode, String groupName);

    void deleteGroup(String groupCode);

    void addUserToGroup(String username, String groupCode);

    void removeUserFromGroup(String username, String groupCode);

    void syncUserGroups(String username, List<String> groupCodes);

    default void syncUserGroupsToExactState(String uid, List<String> groupCodes) {
        syncUserGroups(uid, groupCodes);
    }

    void removeUserFromAllGroups(String username);

    default void removeUserFromAllGroupsIfExists(String uid) {
        removeUserFromAllGroups(uid);
    }
}
