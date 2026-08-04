package com.company.idm.domain.rbac;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 定义角色领域仓储接口与授权关系维护能力。
 */
public interface RoleRepository {

    Optional<Role> findById(Long id);

    Optional<Role> findByCode(String roleCode);

    List<Role> findAll();

    List<Role> findByCodes(Set<String> roleCodes);

    List<Role> findByIds(List<Long> roleIds);

    List<Role> findByScope(RoleScope roleScope);

    List<Role> findByGroupId(Long roleGroupId);

    Role save(Role role);

    void updateStatus(Long id, Integer status);

    void delete(Long id);

    void assignPermissions(Long roleId, List<Long> permissionIds);

    void grantPermissionToRoles(List<Long> roleIds, Long permissionId);

    void removePermissionFromAllRoles(Long permissionId);

    List<Long> findPermissionIdsByRoleId(Long roleId);

    boolean existsUserBinding(Long roleId);
}

