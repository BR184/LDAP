package com.company.idm.application.sync.importplan;

import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ImportBatch;
import com.company.idm.domain.sync.ImportBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImportConsistencyService {

    private final ImportBatchRepository importBatchRepository;
    private final ImportExecutionApplicationService executionService;

    @Transactional
    public ImportBatch retryLdapFailures(Long batchId, String operator) {
        ImportBatch batch = importBatchRepository.findById(batchId).orElseThrow();
        batch.prepareRetry();
        importBatchRepository.save(batch);
        return executionService.executePlan(batchId, operator);
    }
}
