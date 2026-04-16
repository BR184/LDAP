package com.company.idm.interfaces.role;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 封装角色菜单绑定接口的请求参数。
 */
public record BindRoleMenusRequest(@NotNull(message = "菜单列表不能为空") List<Long> menuIds) {
}

