package com.company.idm.interfaces.importplan;

import java.time.LocalDateTime;

public record RollbackItemResponse(
    Long id,
    Long batchId,
    Long changeItemId,
    String targetType,
    String targetKey,
    String rollbackAction,
    String restoreJson,
    String status,
    String errorMessage,
    LocalDateTime executedAt
) {
}
