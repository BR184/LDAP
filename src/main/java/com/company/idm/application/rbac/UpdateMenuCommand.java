package com.company.idm.application.rbac;

import com.company.idm.common.enums.MenuType;

/**
 * 封装更新菜单时的应用层命令参数。
 */
public record UpdateMenuCommand(
    Long menuId,
    String menuName,
    Long parentId,
    MenuType menuType,
    String path,
    String component,
    String icon,
    Integer sortNo,
    String remark
) {
}
