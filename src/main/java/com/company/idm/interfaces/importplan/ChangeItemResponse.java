package com.company.idm.interfaces.importplan;

import java.time.LocalDateTime;

public record ChangeItemResponse(
    Long id,
    Long batchId,
    String targetType,
    String targetKey,
    String changeType,
    String fieldKey,
    String fieldLabel,
    String beforeValue,
    String afterValue,
    Integer objectVersion,
    Boolean defaultEnabled,
    Boolean enabled,
    Boolean requiresConfirmation,
    Boolean confirmed,
    String riskLevel,
    String blockReason,
    String conflictCode,
    java.util.List<String> resolutionOptions,
    String status,
    String errorMessage,
    Integer retryCount,
    LocalDateTime executedAt,
    String resolutionAction,
    String resolvedBy,
    LocalDateTime resolvedAt
) {
}
