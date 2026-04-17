package com.company.idm.interfaces.sync;

/**
 * 封装同步批次响应结构。
 */
public record SyncBatchResponse(
    Long id,
    String batchNo,
    String batchType,
    String sourceType,
    String triggerMode,
    String status,
    String fileName,
    String fileHash,
    String summaryJson,
    String operator,
    String correlationBatchNo
) {
}
