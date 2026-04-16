package com.company.idm.domain.rbac;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 定义菜单领域仓储接口。
 */
public interface MenuRepository {

    List<Menu> findAllEnabled();

    List<Menu> findByRoleCodes(Set<String> roleCodes);

    List<Menu> findByIds(List<Long> menuIds);

    Optional<Menu> findById(Long id);
}

