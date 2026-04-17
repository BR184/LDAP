package com.company.idm.interfaces.sync;

/**
 * 封装同步差异响应结构。
 */
public record SyncDiffResponse(
    Long id,
    String batchNo,
    Long jobId,
    String targetType,
    String targetKey,
    String diffType,
    String sourceSnapshot,
    String targetSnapshot,
    Integer repairable,
    String status
) {
}
