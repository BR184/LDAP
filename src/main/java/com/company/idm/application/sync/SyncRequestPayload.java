package com.company.idm.application.sync;

import com.company.idm.common.enums.SyncTriggerMode;

public record SyncRequestPayload(
    String remark,
    boolean autoRepair,
    String operator,
    SyncTriggerMode triggerMode,
    String documentPath
) {
}
