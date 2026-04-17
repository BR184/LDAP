package com.company.idm.interfaces.sync;

import java.util.List;

/**
 * 封装同步批次详情响应结构。
 */
public record SyncBatchDetailResponse(
    SyncBatchResponse batch,
    List<SyncJobResponse> jobs,
    List<SyncDiffResponse> diffs
) {
}
