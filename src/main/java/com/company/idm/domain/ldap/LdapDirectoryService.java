package com.company.idm.domain.ldap;

import com.company.idm.domain.user.User;
import java.util.List;

/**
 * 定义 LDAP 目录服务的核心操作接口。
 */
public interface LdapDirectoryService {

    boolean authenticate(String userId, String password);

    boolean existsByUid(String userId);

    LdapUserSnapshot findUserSnapshot(String userId);

    List<String> listAllUserIds();

    String createUser(User user, String rawPassword);

    void updateUser(User user);

    default String createOrUpdateUser(User user, String rawPassword) {
        if (existsByUid(user.getUserId())) {
            updateUser(user);
            return findUserSnapshot(user.getUserId()).getDn();
        }
        return createUser(user, rawPassword);
    }

    void enableUser(String userId);

    void disableUser(String userId);

    default void disableUserIfExists(String uid) {
        if (existsByUid(uid)) {
            disableUser(uid);
        }
    }

    default void enableUserIfExists(String uid) {
        if (existsByUid(uid)) {
            enableUser(uid);
        }
    }

    void deleteUser(String userId);

    void resetPassword(String userId, String rawPassword);
}

