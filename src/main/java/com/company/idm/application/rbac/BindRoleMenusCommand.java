package com.company.idm.application.rbac;

import java.util.List;

/**
 * 封装角色绑定菜单时的应用层命令参数。
 */
public record BindRoleMenusCommand(Long roleId, List<Long> menuIds, String operator) {
}

