package com.company.idm.application.sync;

import com.company.idm.common.enums.SyncBatchType;
import com.company.idm.common.enums.SyncDiffStatus;
import com.company.idm.common.enums.SyncJobType;
import com.company.idm.common.enums.SyncRunStatus;
import com.company.idm.common.enums.SyncSourceType;
import com.company.idm.common.enums.SyncTargetType;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.sync.SyncBatch;
import com.company.idm.domain.sync.SyncBatchRepository;
import com.company.idm.domain.sync.SyncDiff;
import com.company.idm.domain.sync.SyncDiffRepository;
import com.company.idm.domain.sync.SyncJob;
import com.company.idm.domain.sync.SyncJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SyncApplicationService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 4000;

    private final List<SyncJobHandler> handlers;
    private final SyncBatchRepository syncBatchRepository;
    private final SyncJobRepository syncJobRepository;
    private final SyncDiffRepository syncDiffRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SyncBatchDetail previewReconcile(String operator, SyncTriggerMode triggerMode) {
        return runBatch(
            SyncSourceType.SYSTEM,
            null,
            false,
            operator,
            triggerMode,
            List.of(
                SyncJobType.LDAP_RECONCILE_DEPARTMENT,
                SyncJobType.LDAP_RECONCILE_USER,
                SyncJobType.LDAP_RECONCILE_MEMBERSHIP
            ),
            true,
            null,
            null
        );
    }

    @Transactional
    public SyncBatchDetail executeReconcile(boolean autoRepair, String operator, SyncTriggerMode triggerMode) {
        return runBatch(
            SyncSourceType.SYSTEM,
            null,
            autoRepair,
            operator,
            triggerMode,
            List.of(
                SyncJobType.LDAP_RECONCILE_DEPARTMENT,
                SyncJobType.LDAP_RECONCILE_USER,
                SyncJobType.LDAP_RECONCILE_MEMBERSHIP
            ),
            false,
            null,
            null
        );
    }

    @Transactional
    public SyncBatchDetail retryJob(Long jobId, String operator) {
        SyncJob job = syncJobRepository.findById(jobId)
            .orElseThrow(() -> new BizException("SYNC_JOB_NOT_FOUND", "同步任务不存在"));
        SyncRequestPayload payload = fromRequestJson(job.getRequestJson());
        return runBatch(
            resolveSourceType(job.getJobType()),
            payload.remark(),
            payload.autoRepair(),
            operator,
            SyncTriggerMode.MANUAL,
            List.of(job.getJobType()),
            false,
            payload.documentPath(),
            job.getBatchNo()
        );
    }

    public List<SyncJob> listJobs() {
        return syncJobRepository.findRecent(100);
    }

    public SyncBatchDetail getBatchDetail(String batchNo) {
        SyncBatch batch = syncBatchRepository.findByBatchNo(batchNo)
            .orElseThrow(() -> new BizException("SYNC_BATCH_NOT_FOUND", "同步批次不存在"));
        return new SyncBatchDetail(
            batch,
            syncJobRepository.findByBatchNo(batchNo),
            syncDiffRepository.findByBatchNo(batchNo)
        );
    }

    private SyncBatchDetail runBatch(
        SyncSourceType sourceType,
        String remark,
        boolean autoRepair,
        String operator,
        SyncTriggerMode triggerMode,
        List<SyncJobType> jobTypes,
        boolean preview,
        String documentPath,
        String correlationBatchNo
    ) {
        SyncBatchType batchType = SyncBatchType.LDAP_RECONCILE;
        if (syncBatchRepository.existsRunningBatch(batchType)) {
            throw new BizException("SYNC_BATCH_RUNNING", "当前已有同类型同步任务正在执行");
        }
        String batchNo = buildBatchNo(batchType);
        SyncBatch batch = syncBatchRepository.save(SyncBatch.builder()
            .batchNo(batchNo)
            .batchType(batchType)
            .sourceType(sourceType)
            .triggerMode(triggerMode)
            .fileName(documentPath)
            .status(SyncRunStatus.RUNNING)
            .operator(operator)
            .correlationBatchNo(correlationBatchNo)
            .startTime(LocalDateTime.now())
            .build());

        int successCount = 0;
        int failCount = 0;
        int totalDiffCount = 0;
        for (SyncJobType jobType : jobTypes) {
            SyncRequestPayload payload = new SyncRequestPayload(
                remark,
                autoRepair,
                operator,
                triggerMode,
                documentPath
            );
            SyncJob job = syncJobRepository.save(SyncJob.builder()
                .batchNo(batchNo)
                .jobType(jobType)
                .targetType(resolveTargetType(jobType))
                .status(SyncRunStatus.RUNNING)
                .requestJson(toJson(payload))
                .operator(operator)
                .retryCount(0)
                .startTime(LocalDateTime.now())
                .build());

            try {
                SyncJobExecutionResult result = handler(jobType).map(currentHandler ->
                    preview ? currentHandler.preview(payload) : currentHandler.execute(payload)
                ).orElseThrow(() -> new BizException("SYNC_HANDLER_NOT_FOUND", "同步处理器不存在"));

                syncJobRepository.save(job.toBuilder()
                    .status(result.status())
                    .resultJson(result.summaryJson())
                    .errorMessage(normalizeErrorMessage(result.errorMessage()))
                    .endTime(LocalDateTime.now())
                    .build());
                List<SyncDiff> diffs = result.diffs().stream()
                    .map(diff -> SyncDiff.builder()
                        .batchNo(batchNo)
                        .jobId(job.getId())
                        .targetType(diff.targetType())
                        .targetKey(diff.targetKey())
                        .diffType(diff.diffType())
                        .sourceSnapshot(diff.sourceSnapshot())
                        .targetSnapshot(diff.targetSnapshot())
                        .repairable(diff.repairable() ? 1 : 0)
                        .status(autoRepair && diff.repairable() ? SyncDiffStatus.REPAIRED : SyncDiffStatus.REPORTED)
                        .build())
                    .toList();
                syncDiffRepository.saveAll(diffs);
                totalDiffCount += diffs.size();
                if (result.status() == SyncRunStatus.SUCCESS || result.status() == SyncRunStatus.PARTIAL_SUCCESS) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception exception) {
                failCount++;
                syncJobRepository.save(job.toBuilder()
                    .status(SyncRunStatus.FAIL)
                    .errorMessage(normalizeErrorMessage(resolveExceptionMessage(exception)))
                    .endTime(LocalDateTime.now())
                    .build());
            }
        }

        SyncRunStatus finalStatus = failCount == 0
            ? SyncRunStatus.SUCCESS
            : (successCount == 0 ? SyncRunStatus.FAIL : SyncRunStatus.PARTIAL_SUCCESS);
        String summaryJson = """
            {"jobCount":%s,"successCount":%s,"failCount":%s,"diffCount":%s,"preview":%s}
            """.formatted(jobTypes.size(), successCount, failCount, totalDiffCount, preview);
        SyncBatch completed = syncBatchRepository.save(batch.toBuilder()
            .status(finalStatus)
            .summaryJson(summaryJson)
            .endTime(LocalDateTime.now())
            .build());
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType(preview ? batchType.name() + "_PREVIEW" : batchType.name() + "_EXECUTE")
            .bizType("SYNC")
            .bizId(batchNo)
            .afterJson(summaryJson)
            .result(finalStatus.name())
            .build());
        return new SyncBatchDetail(
            completed,
            syncJobRepository.findByBatchNo(batchNo),
            syncDiffRepository.findByBatchNo(batchNo)
        );
    }

    private java.util.Optional<SyncJobHandler> handler(SyncJobType jobType) {
        return handlers.stream().filter(handler -> handler.jobType() == jobType).findFirst();
    }

    private SyncSourceType resolveSourceType(SyncJobType jobType) {
        return switch (jobType) {
            case LDAP_RECONCILE_DEPARTMENT, LDAP_RECONCILE_USER, LDAP_RECONCILE_MEMBERSHIP -> SyncSourceType.SYSTEM;
        };
    }

    private SyncTargetType resolveTargetType(SyncJobType jobType) {
        return switch (jobType) {
            case LDAP_RECONCILE_DEPARTMENT -> SyncTargetType.DEPARTMENT;
            case LDAP_RECONCILE_USER -> SyncTargetType.USER;
            case LDAP_RECONCILE_MEMBERSHIP -> SyncTargetType.MEMBERSHIP;
        };
    }

    private String buildBatchNo(SyncBatchType batchType) {
        return batchType.name() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BizException("SYNC_SERIALIZE_FAILED", "同步任务序列化失败");
        }
    }

    private SyncRequestPayload fromRequestJson(String requestJson) {
        try {
            return objectMapper.readValue(requestJson, SyncRequestPayload.class);
        } catch (JsonProcessingException exception) {
            throw new BizException("SYNC_DESERIALIZE_FAILED", "同步任务反序列化失败");
        }
    }

    private String resolveExceptionMessage(Exception exception) {
        if (exception == null) {
            return null;
        }
        String message = exception.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return exception.getClass().getSimpleName();
    }

    private String normalizeErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        String normalized = message.trim();
        if (normalized.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_ERROR_MESSAGE_LENGTH - 14) + "...[truncated]";
    }
}
