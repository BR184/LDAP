package com.company.idm.interfaces.importplan;

import java.time.LocalDateTime;

public record ImportBatchResponse(
    Long id,
    String batchCode,
    String fileName,
    String fileHash,
    String sourceType,
    Integer totalItems,
    Integer enabledItems,
    Integer conflictItems,
    String status,
    String createdBy,
    LocalDateTime createdAt,
    String confirmedBy,
    LocalDateTime confirmedAt,
    String executedBy,
    LocalDateTime executedAt,
    String rollbackBy,
    LocalDateTime rollbackAt,
    LocalDateTime expiredAt,
    String remark
) {
}
