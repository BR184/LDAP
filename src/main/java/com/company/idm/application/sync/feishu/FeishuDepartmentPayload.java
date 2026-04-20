package com.company.idm.application.sync.feishu;

/**
 * 封装飞书部门标准化载荷。
 */
public record FeishuDepartmentPayload(
    String externalId,
    String departmentCode,
    String departmentName,
    String parentExternalId,
    Integer status,
    Integer orderNo
) {
}
