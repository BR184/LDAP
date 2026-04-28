package com.company.idm.application.rbac;

import java.util.List;

/**
 * 封装批量删除角色时的应用层命令参数。
 */
public record BatchDeleteRolesCommand(List<Long> roleIds, String operator) {
}
