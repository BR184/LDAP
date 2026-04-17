package com.company.idm.application.sync;

import com.company.idm.common.enums.SyncJobType;

/**
 * 定义同步任务处理器接口。
 */
public interface SyncJobHandler {

    SyncJobType jobType();

    SyncJobExecutionResult preview(SyncRequestPayload payload);

    SyncJobExecutionResult execute(SyncRequestPayload payload);
}
