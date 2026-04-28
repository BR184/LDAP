package com.company.idm.interfaces.role;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 封装批量删除角色接口请求参数。
 */
public record BatchDeleteRolesRequest(
    @NotEmpty(message = "待删除角色不能为空")
    List<@NotNull(message = "角色ID不能为空") Long> roleIds
) {
}
