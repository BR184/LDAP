package com.company.idm.interfaces.sync;

import com.company.idm.application.sync.SyncBatchDetail;
import com.company.idm.domain.sync.SyncBatch;
import com.company.idm.domain.sync.SyncDiff;
import com.company.idm.domain.sync.SyncJob;
import org.springframework.stereotype.Component;

/**
 * 负责将同步领域对象组装为接口响应对象。
 */
@Component
public class SyncResponseAssembler {

    public SyncBatchDetailResponse toResponse(SyncBatchDetail detail) {
        return new SyncBatchDetailResponse(
            toBatchResponse(detail.batch()),
            detail.jobs().stream().map(this::toJobResponse).toList(),
            detail.diffs().stream().map(this::toDiffResponse).toList()
        );
    }

    public SyncBatchResponse toBatchResponse(SyncBatch batch) {
        return new SyncBatchResponse(
            batch.getId(),
            batch.getBatchNo(),
            batch.getBatchType().name(),
            batch.getSourceType().name(),
            batch.getTriggerMode().name(),
            batch.getStatus().name(),
            batch.getFileName(),
            batch.getFileHash(),
            batch.getSummaryJson(),
            batch.getOperator(),
            batch.getCorrelationBatchNo()
        );
    }

    public SyncJobResponse toJobResponse(SyncJob job) {
        return new SyncJobResponse(
            job.getId(),
            job.getBatchNo(),
            job.getJobType().name(),
            job.getTargetType().name(),
            job.getStatus().name(),
            job.getRequestJson(),
            job.getResultJson(),
            job.getErrorMessage(),
            job.getOperator(),
            job.getRetryCount()
        );
    }

    public SyncDiffResponse toDiffResponse(SyncDiff diff) {
        return new SyncDiffResponse(
            diff.getId(),
            diff.getBatchNo(),
            diff.getJobId(),
            diff.getTargetType().name(),
            diff.getTargetKey(),
            diff.getDiffType().name(),
            diff.getSourceSnapshot(),
            diff.getTargetSnapshot(),
            diff.getRepairable(),
            diff.getStatus().name()
        );
    }
}
