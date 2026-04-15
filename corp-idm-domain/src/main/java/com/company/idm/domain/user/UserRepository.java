package com.company.idm.domain.user;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    List<User> findAll();

    User save(User user);

    void updateStatus(Long id, Integer statusCode, Integer tokenVersion);

    void assignRoles(Long userId, List<Long> roleIds);

    Set<String> findRoleCodesByUsername(String username);

    List<UserRoleBinding> listUserRoleBindings();
}

