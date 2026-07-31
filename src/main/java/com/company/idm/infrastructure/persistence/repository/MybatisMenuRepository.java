package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.common.enums.MenuType;
import com.company.idm.domain.rbac.Menu;
import com.company.idm.domain.rbac.MenuRepository;
import com.company.idm.infrastructure.persistence.dataobject.MenuDO;
import com.company.idm.infrastructure.persistence.dataobject.RoleMenuDO;
import com.company.idm.infrastructure.persistence.mapper.MenuMapper;
import com.company.idm.infrastructure.persistence.mapper.RoleMenuMapper;
import java.time.LocalDateTime;
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
    private final RoleMenuMapper roleMenuMapper;

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
    public List<Menu> findByAccess(Set<String> roleCodes, Set<String> permissionCodes) {
        if ((roleCodes == null || roleCodes.isEmpty())
            && (permissionCodes == null || permissionCodes.isEmpty())) {
            return Collections.emptyList();
        }
        return menuMapper.selectByAccess(roleCodes, permissionCodes).stream()
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

    @Override
    public Optional<Menu> findByCode(String menuCode) {
        return Optional.ofNullable(menuMapper.selectOne(new LambdaQueryWrapper<MenuDO>()
            .eq(MenuDO::getMenuCode, menuCode)))
            .map(this::toDomain);
    }

    @Override
    public Menu save(Menu menu) {
        MenuDO dataObject = toDataObject(menu);
        if (dataObject.getId() == null) {
            dataObject.setGmtCreate(LocalDateTime.now());
            dataObject.setGmtModified(LocalDateTime.now());
            menuMapper.insert(dataObject);
        } else {
            dataObject.setGmtModified(LocalDateTime.now());
            menuMapper.updateById(dataObject);
        }
        return toDomain(dataObject);
    }

    @Override
    public boolean existsChildren(Long parentId) {
        return menuMapper.selectCount(new LambdaQueryWrapper<MenuDO>()
            .eq(MenuDO::getParentId, parentId)) > 0;
    }

    @Override
    public boolean existsRoleBindingConflict(Long menuId, Integer minPermissionLevel) {
        return menuMapper.countRoleBindingConflict(menuId, minPermissionLevel) > 0;
    }

    @Override
    public void bindRole(Long roleId, Long menuId) {
        long count = roleMenuMapper.selectCount(new LambdaQueryWrapper<RoleMenuDO>()
            .eq(RoleMenuDO::getRoleId, roleId)
            .eq(RoleMenuDO::getMenuId, menuId));
        if (count > 0) {
            return;
        }
        RoleMenuDO relation = new RoleMenuDO();
        relation.setRoleId(roleId);
        relation.setMenuId(menuId);
        relation.setCreator("system");
        relation.setGmtCreate(LocalDateTime.now());
        roleMenuMapper.insert(relation);
    }

    @Override
    public void removeRoleBindings(Long menuId) {
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenuDO>()
            .eq(RoleMenuDO::getMenuId, menuId));
    }

    @Override
    public void delete(Long id) {
        menuMapper.deleteById(id);
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

    private MenuDO toDataObject(Menu menu) {
        MenuDO dataObject = new MenuDO();
        dataObject.setId(menu.getId());
        dataObject.setMenuCode(menu.getMenuCode());
        dataObject.setMenuName(menu.getMenuName());
        dataObject.setParentId(menu.getParentId());
        dataObject.setMenuType(menu.getMenuType().name());
        dataObject.setPath(menu.getPath());
        dataObject.setComponent(menu.getComponent());
        dataObject.setIcon(menu.getIcon());
        dataObject.setSortNo(menu.getSortNo());
        dataObject.setStatus(menu.getStatus());
        dataObject.setVisible(menu.getVisible());
        dataObject.setMinPermissionLevel(menu.getMinPermissionLevel());
        dataObject.setRemark(menu.getRemark());
        dataObject.setCreator("system");
        dataObject.setModifier("system");
        return dataObject;
    }
}
