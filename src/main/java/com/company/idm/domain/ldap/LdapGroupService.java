package com.company.idm.domain.ldap;

import java.util.List;

/**
 * 定义 LDAP 分组目录的操作接口。
 */
public interface LdapGroupService {

    String createGroup(String groupCode, String groupName);

    void updateGroup(String groupCode, String groupName);

    void deleteGroup(String groupCode);

    void addUserToGroup(String username, String groupCode);

    void removeUserFromGroup(String username, String groupCode);

    void syncUserGroups(String username, List<String> groupCodes);

    void removeUserFromAllGroups(String username);
}
