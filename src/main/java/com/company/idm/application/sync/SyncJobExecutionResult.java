package com.company.idm.application.sync;

import com.company.idm.common.enums.SyncRunStatus;
import java.util.List;

/**
 * 封装同步任务执行结果。
 */
public record SyncJobExecutionResult(
    SyncRunStatus status,
    String summaryJson,
    String errorMessage,
    List<SyncDiffPayload> diffs
) {
}
