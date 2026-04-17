package com.company.idm.application.sync;

import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncTargetType;

/**
 * 封装同步执行器返回的差异项载荷。
 */
public record SyncDiffPayload(
    SyncTargetType targetType,
    String targetKey,
    SyncDiffType diffType,
    String sourceSnapshot,
    String targetSnapshot,
    boolean repairable
) {
}
