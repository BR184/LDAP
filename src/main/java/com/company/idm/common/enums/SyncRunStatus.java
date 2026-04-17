package com.company.idm.common.enums;

/**
 * 定义同步批次与任务运行状态枚举。
 */
public enum SyncRunStatus {
    INIT,
    RUNNING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAIL,
    RETRY
}
