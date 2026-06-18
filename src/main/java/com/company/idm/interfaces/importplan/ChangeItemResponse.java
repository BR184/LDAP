package com.company.idm.interfaces.importplan;

import java.time.LocalDateTime;

public record ChangeItemResponse(
    Long id,
    Long batchId,
    String targetType,
    String targetKey,
    String changeType,
    String fieldName,
    String beforeValue,
    String afterValue,
    String beforeJson,
    String afterJson,
    Integer objectVersion,
    Boolean defaultEnabled,
    Boolean enabled,
    Boolean requiresConfirmation,
    Boolean confirmed,
    String riskLevel,
    String blockReason,
    String status,
    String errorMessage,
    Integer retryCount,
    LocalDateTime executedAt
) {
}
