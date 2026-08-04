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

    Optional<User> findByEmployeeNo(String employeeNo);

    Optional<User> findByIntranetEmail(String intranetEmail);

    List<User> findAll();

    List<User> findActiveEmployees();

    List<User> findByConditions(String keyword, String deptCode, Boolean accessAllowed);

    List<PublicUser> searchPublicUsers(String realNameKeyword, int limit);

    List<PublicUser> findPublicUsersByRoleId(Long roleId);

    List<Long> findRoleIdsByUserId(Long userId);

    User save(User user);

    void updateProfile(User user);

    void updateAccessAllowed(Long id, boolean accessAllowed, Integer tokenVersion, String operator);

    void logicalDelete(Long id, String recycledUsername, Integer tokenVersion);

    void bumpTokenVersion(Long id, Integer tokenVersion);

    void assignRoles(Long userId, List<Long> roleIds, String operator);

    void syncRoleBindings(Long roleId, Set<Long> expectedUserIds, String operator);

    void removeAllRoles(Long userId, String operator);

    void addRole(Long userId, Long roleId, String operator);

    void removeRole(Long userId, Long roleId, String operator);

    Set<String> findRoleCodesByUserId(String userId);

    List<UserRoleBinding> listUserRoleBindings();

    boolean existsActiveDeptBinding(String deptCode);
}

