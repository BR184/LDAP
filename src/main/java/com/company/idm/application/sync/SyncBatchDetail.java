package com.company.idm.application.sync;

import com.company.idm.domain.sync.SyncBatch;
import com.company.idm.domain.sync.SyncDiff;
import com.company.idm.domain.sync.SyncJob;
import java.util.List;

/**
 * 封装同步批次详情聚合视图。
 */
public record SyncBatchDetail(SyncBatch batch, List<SyncJob> jobs, List<SyncDiff> diffs) {
}
