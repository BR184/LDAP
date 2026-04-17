package com.company.idm.interfaces.sync;

/**
 * 封装同步任务响应结构。
 */
public record SyncJobResponse(
    Long id,
    String batchNo,
    String jobType,
    String targetType,
    String status,
    String requestJson,
    String resultJson,
    String errorMessage,
    String operator,
    Integer retryCount
) {
}
