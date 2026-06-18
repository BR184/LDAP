package com.company.idm.interfaces.importplan;

import java.util.List;

public record UserReviewRowResponse(
    String targetKey,
    String realName,
    String employeeNo,
    String deptCode,
    String jobTitle,
    String leaderRef,
    String directLeaderRaw,
    String status,
    String employmentStatus,
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
