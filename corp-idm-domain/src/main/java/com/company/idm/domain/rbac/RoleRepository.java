package com.company.idm.domain.rbac;

import java.util.List;
import java.util.Optional;

public interface RoleRepository {

    Optional<Role> findById(Long id);

    Optional<Role> findByCode(String roleCode);

    List<Role> findAll();

    Role save(Role role);

    void assignPermissions(Long roleId, List<Long> permissionIds);
}

