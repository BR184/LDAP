package com.company.idm.interfaces.importplan;

import java.util.List;

public record DepartmentReviewRowResponse(
    String targetKey,
    String deptName,
    String parentDeptCode,
    String status,
    String changeType,
    String changeSummary,
    String riskLevel,
    boolean enabled,
    boolean requiresConfirmation,
    boolean confirmed,
    List<Long> itemIds,
    List<FieldChangeResponse> fieldChanges
) {
}
