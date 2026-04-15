package com.company.idm.domain.rbac;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository {

    List<Permission> findAll();

    List<RolePolicy> listRolePolicies();

    Optional<Permission> findById(Long id);
}

