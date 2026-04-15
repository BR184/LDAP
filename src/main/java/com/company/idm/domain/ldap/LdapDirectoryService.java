package com.company.idm.domain.ldap;

import com.company.idm.domain.user.User;

/**
 * 定义 LDAP 目录服务的核心操作接口。
 */
public interface LdapDirectoryService {

    boolean authenticate(String username, String password);

    boolean existsByUid(String username);

    String createUser(User user, String rawPassword);

    void enableUser(String username);

    void disableUser(String username);

    void resetPassword(String username, String rawPassword);
}

