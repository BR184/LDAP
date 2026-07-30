package com.company.idm.application.sync.importplan;

import com.company.idm.application.sync.feishu.FeishuDepartmentPayload;
import com.company.idm.application.sync.feishu.FeishuUserPayload;
import java.util.Set;

public record ImportFileConflictIndex(
    Set<String> duplicateDepartmentExternalIds,
    Set<String> duplicateDepartmentCodes,
    Set<String> duplicateUserIds,
    Set<String> duplicateEmployeeNos
) {

    public ImportFileConflictIndex {
        duplicateDepartmentExternalIds = Set.copyOf(duplicateDepartmentExternalIds);
        duplicateDepartmentCodes = Set.copyOf(duplicateDepartmentCodes);
        duplicateUserIds = Set.copyOf(duplicateUserIds);
        duplicateEmployeeNos = Set.copyOf(duplicateEmployeeNos);
    }

    public boolean blocksDepartment(FeishuDepartmentPayload payload) {
        return payload != null && (
            duplicateDepartmentExternalIds.contains(normalize(payload.externalId()))
                || duplicateDepartmentCodes.contains(normalize(payload.departmentCode()))
        );
    }

    public boolean blocksUser(FeishuUserPayload payload) {
        return payload != null && (
            duplicateUserIds.contains(normalize(payload.userId()))
                || duplicateEmployeeNos.contains(normalize(payload.employeeNo()))
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
