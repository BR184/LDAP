package com.company.idm.domain.sync;

import java.util.List;

/**
 * 定义同步差异领域仓储接口。
 */
public interface SyncDiffRepository {

    void saveAll(List<SyncDiff> diffs);

    List<SyncDiff> findByBatchNo(String batchNo);
}
