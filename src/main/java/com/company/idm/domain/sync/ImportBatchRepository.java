package com.company.idm.domain.sync;

import java.util.List;
import java.util.Optional;

public interface ImportBatchRepository {

    ImportBatch save(ImportBatch batch);

    Optional<ImportBatch> findById(Long id);

    Optional<ImportBatch> findByBatchCode(String batchCode);

    List<ImportBatch> findRecent(int limit);

    boolean existsActiveFeishuImportBatch();
}
