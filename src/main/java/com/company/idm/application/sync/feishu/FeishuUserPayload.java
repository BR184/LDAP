package com.company.idm.application.sync.feishu;

/**
 * 封装飞书用户标准化载荷。
 */
public record FeishuUserPayload(
    String externalId,
    String username,
    String realName,
    String email,
    String mobile,
    String employeeNo,
    String mainDepartmentExternalId,
    Integer status,
    Integer orderNo
) {
}
