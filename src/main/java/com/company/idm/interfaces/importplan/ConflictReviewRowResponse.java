package com.company.idm.interfaces.importplan;

public record ConflictReviewRowResponse(
    Long itemId,
    String targetType,
    String targetKey,
    String candidateRealName,
    String employeeNo,
    String existingUserId,
    String existingRealName,
    String blockReason,
    String conflictCode,
    java.util.List<String> resolutionOptions,
    String riskLevel,
    String status,
    String errorMessage,
    String resolutionAction,
    String resolvedBy,
    java.time.LocalDateTime resolvedAt
) {
}
