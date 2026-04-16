package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.common.enums.MenuType;
import com.company.idm.domain.rbac.Menu;
import com.company.idm.domain.rbac.MenuRepository;
import com.company.idm.infrastructure.persistence.dataobject.MenuDO;
import com.company.idm.infrastructure.persistence.mapper.MenuMapper;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 实现菜单仓储。
 */
@Repository
@RequiredArgsConstructor
public class MybatisMenuRepository implements MenuRepository {

    private final MenuMapper menuMapper;

    @Override
    public List<Menu> findAllEnabled() {
        return menuMapper.selectList(new LambdaQueryWrapper<MenuDO>()
                .eq(MenuDO::getStatus, 1)
                .eq(MenuDO::getVisible, 1)
                .orderByAsc(MenuDO::getSortNo, MenuDO::getId))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Menu> findByRoleCodes(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        return menuMapper.selectByRoleCodes(roleCodes).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Menu> findByIds(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        return menuMapper.selectBatchIds(menuIds).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<Menu> findById(Long id) {
        return Optional.ofNullable(menuMapper.selectById(id)).map(this::toDomain);
    }

    private Menu toDomain(MenuDO dataObject) {
        return Menu.builder()
            .id(dataObject.getId())
            .menuCode(dataObject.getMenuCode())
            .menuName(dataObject.getMenuName())
            .parentId(dataObject.getParentId())
            .menuType(MenuType.valueOf(dataObject.getMenuType()))
            .path(dataObject.getPath())
            .component(dataObject.getComponent())
            .icon(dataObject.getIcon())
            .sortNo(dataObject.getSortNo())
            .status(dataObject.getStatus())
            .visible(dataObject.getVisible())
            .minPermissionLevel(dataObject.getMinPermissionLevel())
            .remark(dataObject.getRemark())
            .build();
    }
}
