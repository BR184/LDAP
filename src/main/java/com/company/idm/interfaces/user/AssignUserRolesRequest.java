package com.company.idm.interfaces.user;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 封装用户分配角色接口的请求参数。
 */
public record AssignUserRolesRequest(@NotEmpty(message = "角色列表不能为空") List<Long> roleIds) {
}
