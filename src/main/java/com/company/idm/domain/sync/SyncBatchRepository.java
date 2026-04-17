package com.company.idm.domain.sync;

import com.company.idm.common.enums.SyncBatchType;
import java.util.Optional;

/**
 * 定义同步批次领域仓储接口。
 */
public interface SyncBatchRepository {

    SyncBatch save(SyncBatch batch);

    Optional<SyncBatch> findByBatchNo(String batchNo);

    boolean existsRunningBatch(SyncBatchType batchType);
}
