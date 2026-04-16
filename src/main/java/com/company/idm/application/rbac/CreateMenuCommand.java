package com.company.idm.application.rbac;

import com.company.idm.common.enums.MenuType;

/**
 * 封装创建菜单时的应用层命令参数。
 */
public record CreateMenuCommand(
    String menuCode,
    String menuName,
    Long parentId,
    MenuType menuType,
    String path,
    String component,
    String icon,
    Integer sortNo,
    Integer minPermissionLevel,
    String remark
) {
}
