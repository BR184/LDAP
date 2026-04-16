package com.company.idm.interfaces.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * 封装菜单树节点的响应结构。
 */
public record MenuTreeNodeResponse(
    Long id,
    String menuCode,
    String menuName,
    String menuType,
    String path,
    String component,
    String icon,
    Long parentId,
    Integer sortNo,
    List<MenuTreeNodeResponse> children
) {

    public static MenuTreeNodeResponse create(
        Long id,
        String menuCode,
        String menuName,
        String menuType,
        String path,
        String component,
        String icon,
        Long parentId,
        Integer sortNo
    ) {
        return new MenuTreeNodeResponse(id, menuCode, menuName, menuType, path, component, icon, parentId, sortNo, new ArrayList<>());
    }
}

