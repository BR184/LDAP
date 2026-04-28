package com.company.idm.interfaces.user;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 封装批量删除用户接口请求参数。
 */
public record BatchDeleteUsersRequest(
    @NotEmpty(message = "待删除用户不能为空")
    List<@NotNull(message = "用户ID不能为空") Long> userIds
) {
}
