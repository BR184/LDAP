package com.company.idm.application.rbac;

/**
 * 封装删除菜单时的应用层命令参数。
 */
public record DeleteMenuCommand(Long menuId, String operator) {
}
