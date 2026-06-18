package com.company.idm.interfaces.importplan;

public record ConflictReviewRowResponse(
    Long itemId,
    String targetType,
    String targetKey,
    String blockReason,
    String riskLevel,
    String status,
    String errorMessage
) {
}
