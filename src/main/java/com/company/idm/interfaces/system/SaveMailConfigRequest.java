package com.company.idm.interfaces.system;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 保存邮件配置请求。
 */
public record SaveMailConfigRequest(
    @NotBlank(message = "发送模式不能为空") String sendMode,
    @NotBlank(message = "加密方式不能为空") String secureMode,
    @NotBlank(message = "SMTP 服务器地址不能为空") String host,
    @Min(value = 1, message = "SMTP 端口范围必须在 1 到 65535 之间")
    @Max(value = 65535, message = "SMTP 端口范围必须在 1 到 65535 之间") Integer port,
    @NotBlank(message = "发件邮箱不能为空")
    @Email(message = "发件邮箱格式错误") String fromAddress,
    String fromName,
    Boolean authRequired,
    String username,
    String password,
    Boolean enabled,
    String remark
) {
}
