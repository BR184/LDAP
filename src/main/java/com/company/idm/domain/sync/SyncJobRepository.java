package com.company.idm.domain.sync;

import java.util.List;
import java.util.Optional;

/**
 * 定义同步任务领域仓储接口。
 */
public interface SyncJobRepository {

    SyncJob save(SyncJob job);

    Optional<SyncJob> findById(Long id);

    List<SyncJob> findByBatchNo(String batchNo);

    List<SyncJob> findRecent(int limit);
}
