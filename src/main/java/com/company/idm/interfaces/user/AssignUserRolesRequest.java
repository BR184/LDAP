package com.company.idm.interfaces.user;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 封装用户分配角色接口的请求参数。
 */
public record AssignUserRolesRequest(
    @NotEmpty(message = "角色列表不能为空") List<@NotNull(message = "角色ID不能为空") Long> roleIds,
    List<@NotNull(message = "预期角色ID不能为空") Long> expectedRoleIds
) {
}
