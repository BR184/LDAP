package com.company.idm.domain.rbac;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 定义权限领域仓储接口与策略查询能力。
 */
public interface PermissionRepository {

    List<Permission> findAll();

    List<RolePolicy> listRolePolicies();

    Optional<Permission> findById(Long id);

    Optional<Permission> findByCode(String permissionCode);

    Permission save(Permission permission);

    void delete(Long permissionId);

    Set<String> findPermissionCodesByUserId(String userId);
}

