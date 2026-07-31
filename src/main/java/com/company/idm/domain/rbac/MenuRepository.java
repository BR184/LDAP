package com.company.idm.domain.rbac;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 定义菜单领域仓储接口。
 */
public interface MenuRepository {

    List<Menu> findAllEnabled();

    List<Menu> findVisibleByPermissionCodes(Set<String> permissionCodes);

    Optional<Menu> findById(Long id);

    Optional<Menu> findByCode(String menuCode);

    Menu save(Menu menu);

    boolean existsChildren(Long parentId);

    void bindVisibilityPermission(Long menuId, Long permissionId);

    Optional<Long> findVisibilityPermissionId(Long menuId);

    void removeVisibilityPermission(Long menuId);

    void delete(Long id);
}
