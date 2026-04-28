package com.company.idm.interfaces.sync;

import java.time.LocalDateTime;

/**
 * 封装同步任务响应结构。
 */
public record SyncJobResponse(
    Long id,
    String batchNo,
    String jobType,
    String targetType,
    LocalDateTime startTime,
    String status,
    String requestJson,
    String resultJson,
    String errorMessage,
    String operator,
    Integer retryCount
) {
}
