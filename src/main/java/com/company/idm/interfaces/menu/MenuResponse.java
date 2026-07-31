package com.company.idm.interfaces.menu;

/**
 * 封装菜单详情与管理视图的响应结构。
 */
public record MenuResponse(
    Long id,
    String menuCode,
    String menuName,
    Long parentId,
    String menuType,
    String path,
    String component,
    String icon,
    Integer sortNo,
    String remark
) {
}
