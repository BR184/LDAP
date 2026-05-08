package com.company.idm.domain.user;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 定义用户领域仓储接口与角色绑定查询能力。
 */
public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByUserId(String userId);

    default Optional<User> findByUsername(String username) {
        return findByUserId(username);
    }

    default Optional<User> findByExternalId(String externalId) {
        return findByUserId(externalId);
    }

    Optional<User> findByEmployeeNo(String employeeNo);

    Optional<User> findByIntranetEmail(String intranetEmail);

    List<User> findAll();

    List<User> findByConditions(String keyword, String deptCode, Integer statusCode);

    User save(User user);

    void updateProfile(User user);

    void updateStatus(Long id, Integer statusCode, Integer tokenVersion);

    void logicalDelete(Long id, String recycledUsername, Integer tokenVersion);

    void bumpTokenVersion(Long id, Integer tokenVersion);

    void assignRoles(Long userId, List<Long> roleIds);

    void removeAllRoles(Long userId);

    Set<String> findRoleCodesByUserId(String userId);

    default Set<String> findRoleCodesByUsername(String username) {
        return findRoleCodesByUserId(username);
    }

    List<UserRoleBinding> listUserRoleBindings();

    boolean existsActiveDeptBinding(String deptCode);
}

