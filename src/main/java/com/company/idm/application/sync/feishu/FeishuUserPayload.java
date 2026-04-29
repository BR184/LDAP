package com.company.idm.application.sync.feishu;

import java.util.List;

/**
 * Encapsulate standardized FEISHU user payload.
 */
public record FeishuUserPayload(
    String externalId,
    String username,
    String realName,
    String email,
    String mobile,
    String employeeNo,
    String mainDepartmentExternalId,
    List<String> partTimeDepartmentExternalIds,
    Integer status,
    Integer orderNo
) {
    public FeishuUserPayload(
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
        this(externalId, username, realName, email, mobile, employeeNo, mainDepartmentExternalId, List.of(), status, orderNo);
    }
}
