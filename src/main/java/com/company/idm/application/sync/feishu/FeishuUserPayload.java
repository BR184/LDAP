package com.company.idm.application.sync.feishu;

import java.util.List;

/**
 * Encapsulate standardized FEISHU user payload.
 */
public record FeishuUserPayload(
    String userId,
    String realName,
    String email,
    String mobile,
    String employeeNo,
    String jobTitle,
    String directLeaderRaw,
    String accountStatus,
    String mainDepartmentExternalId,
    List<String> partTimeDepartmentExternalIds,
    Integer status,
    Integer orderNo
) {
    public FeishuUserPayload(
        String userId,
        String realName,
        String email,
        String mobile,
        String employeeNo,
        String mainDepartmentExternalId,
        List<String> partTimeDepartmentExternalIds,
        Integer status,
        Integer orderNo
    ) {
        this(
            userId,
            realName,
            email,
            mobile,
            employeeNo,
            null,
            null,
            null,
            mainDepartmentExternalId,
            partTimeDepartmentExternalIds,
            status,
            orderNo
        );
    }

    public FeishuUserPayload(
        String userId,
        String realName,
        String email,
        String mobile,
        String employeeNo,
        String mainDepartmentExternalId,
        Integer status,
        Integer orderNo
    ) {
        this(
            userId,
            realName,
            email,
            mobile,
            employeeNo,
            null,
            null,
            null,
            mainDepartmentExternalId,
            List.of(),
            status,
            orderNo
        );
    }

    public FeishuUserPayload(
        String userId,
        String realName,
        String email,
        String mobile,
        String employeeNo,
        String jobTitle,
        String directLeaderRaw,
        String accountStatus,
        String mainDepartmentExternalId,
        Integer status,
        Integer orderNo
    ) {
        this(
            userId,
            realName,
            email,
            mobile,
            employeeNo,
            jobTitle,
            directLeaderRaw,
            accountStatus,
            mainDepartmentExternalId,
            List.of(),
            status,
            orderNo
        );
    }
}
