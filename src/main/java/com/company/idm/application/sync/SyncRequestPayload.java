package com.company.idm.application.sync;

import com.company.idm.common.enums.SyncTriggerMode;

/**
 * 封装同步任务的公共请求载荷。
 */
public record SyncRequestPayload(
    boolean forceFullSync,
    String remark,
    boolean autoRepair,
    String operator,
    SyncTriggerMode triggerMode,
    String documentPath
) {
}
