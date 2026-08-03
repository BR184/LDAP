package com.company.idm.interfaces.token;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record CreatePersonalAccessTokenRequest(
    @NotBlank(message = "令牌名称不能为空")
    @Size(max = 64, message = "令牌名称不能超过64个字符")
    String name,
    @Future(message = "令牌有效期必须晚于当前时间")
    LocalDateTime expiresAt,
    @NotEmpty(message = "请至少选择一项API权限")
    @Size(max = 100, message = "单个令牌最多选择100项API权限")
    List<@NotNull(message = "权限ID不能为空") Long> permissionIds,
    @NotBlank(message = "请先验证旧密码")
    String passwordVerificationToken
) {
}
