package com.company.idm.application.sync;

import com.company.idm.common.enums.ImportMode;
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

/**
 * 同步应用服务，负责同步批次、任务、差异和手工/定时触发编排。
 */
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

    /**
     * 手工或定时执行飞书部门同步批次。
     */
    @Transactional
    public SyncBatchDetail executeFeishuDepartmentSync(String operator, SyncTriggerMode triggerMode) {
        return runBatch(
            SyncBatchType.FEISHU_IMPORT,
            SyncSourceType.FEISHU,
            false,
            null,
            false,
            operator,
            triggerMode,
            List.of(SyncJobType.FEISHU_DEPARTMENT_IMPORT),
            false,
            null,
            null
        );
    }

    /**
     * 手工或定时执行飞书用户同步批次。
     * 为确保主部门映射完整，当前会先执行部门同步，再执行用户同步。
     */
    @Transactional
    public SyncBatchDetail executeFeishuUserSync(String operator, SyncTriggerMode triggerMode) {
        return runBatch(
            SyncBatchType.FEISHU_IMPORT,
            SyncSourceType.FEISHU,
            false,
            null,
            false,
            operator,
            triggerMode,
            List.of(SyncJobType.FEISHU_DEPARTMENT_IMPORT, SyncJobType.FEISHU_USER_IMPORT),
            false,
            null,
            null
        );
    }

    /**
     * 手工执行飞书部门标准化文件导入批次。
     */
    @Transactional
    public SyncBatchDetail executeFeishuDepartmentFileImport(
        String documentPath,
        boolean forceFullSync,
        String remark,
        String operator,
        SyncTriggerMode triggerMode
    ) {
        return runBatch(
            SyncBatchType.FEISHU_IMPORT,
            SyncSourceType.FEISHU,
            forceFullSync,
            remark,
            false,
            operator,
            triggerMode,
            List.of(SyncJobType.FEISHU_DEPARTMENT_IMPORT),
            false,
            documentPath,
            null
        );
    }

    /**
     * 手工执行飞书用户标准化文件导入批次。
     */
    @Transactional
    public SyncBatchDetail executeFeishuUserFileImport(
        String documentPath,
        boolean forceFullSync,
        String remark,
        String operator,
        SyncTriggerMode triggerMode
    ) {
        return runBatch(
            SyncBatchType.FEISHU_IMPORT,
            SyncSourceType.FEISHU,
            forceFullSync,
            remark,
            false,
            operator,
            triggerMode,
            List.of(SyncJobType.FEISHU_USER_IMPORT),
            false,
            documentPath,
            null
        );
    }

    /**
     * 手工执行飞书一键文件导入批次。
     */
    @Transactional
    public SyncBatchDetail executeFeishuFullFileImport(
        String documentPath,
        ImportMode importMode,
        String remark,
        String operator,
        SyncTriggerMode triggerMode
    ) {
        return runBatch(
            SyncBatchType.FEISHU_IMPORT,
            SyncSourceType.FEISHU,
            importMode == ImportMode.ALIGN,
            remark,
            false,
            operator,
            triggerMode,
            List.of(SyncJobType.FEISHU_FULL_IMPORT),
            false,
            documentPath,
            null,
            importMode
        );
    }

    /**
     * 手工或定时预览 LDAP 对账批次。
     */
    @Transactional
    public SyncBatchDetail previewReconcile(String operator, SyncTriggerMode triggerMode) {
        return runBatch(
            SyncBatchType.LDAP_RECONCILE,
            SyncSourceType.SYSTEM,
            false,
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

    /**
     * 手工或定时执行 LDAP 对账批次，可选择自动修复可修复差异。
     */
    @Transactional
    public SyncBatchDetail executeReconcile(boolean autoRepair, String operator, SyncTriggerMode triggerMode) {
        return runBatch(
            SyncBatchType.LDAP_RECONCILE,
            SyncSourceType.SYSTEM,
            false,
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

    /**
     * 按任务重新发起一次重试批次。
     */
    @Transactional
    public SyncBatchDetail retryJob(Long jobId, String operator) {
        SyncJob job = syncJobRepository.findById(jobId)
            .orElseThrow(() -> new BizException("SYNC_JOB_NOT_FOUND", "同步任务不存在"));
        SyncRequestPayload payload = fromRequestJson(job.getRequestJson());
        return runBatch(
            resolveBatchType(job.getJobType()),
            resolveSourceType(job.getJobType()),
            payload.forceFullSync(),
            payload.remark(),
            payload.autoRepair(),
            operator,
            SyncTriggerMode.MANUAL,
            List.of(job.getJobType()),
            false,
            payload.documentPath(),
            job.getBatchNo(),
            payload.importMode()
        );
    }

    /**
     * 查询最近的同步任务列表。
     */
    public List<SyncJob> listJobs() {
        return syncJobRepository.findRecent(100);
    }

    /**
     * 查询同步批次详情。
     */
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
        SyncBatchType batchType,
        SyncSourceType sourceType,
        boolean forceFullSync,
        String remark,
        boolean autoRepair,
        String operator,
        SyncTriggerMode triggerMode,
        List<SyncJobType> jobTypes,
        boolean preview,
        String documentPath,
        String correlationBatchNo
    ) {
        return runBatch(
            batchType,
            sourceType,
            forceFullSync,
            remark,
            autoRepair,
            operator,
            triggerMode,
            jobTypes,
            preview,
            documentPath,
            correlationBatchNo,
            forceFullSync ? ImportMode.ALIGN : ImportMode.SUPPLEMENT
        );
    }

    private SyncBatchDetail runBatch(
        SyncBatchType batchType,
        SyncSourceType sourceType,
        boolean forceFullSync,
        String remark,
        boolean autoRepair,
        String operator,
        SyncTriggerMode triggerMode,
        List<SyncJobType> jobTypes,
        boolean preview,
        String documentPath,
        String correlationBatchNo,
        ImportMode importMode
    ) {
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
            .fileHash(importMode == null ? null : importMode.name())
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
                forceFullSync,
                remark,
                autoRepair,
                operator,
                triggerMode,
                documentPath,
                importMode
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

    private SyncBatchType resolveBatchType(SyncJobType jobType) {
        return switch (jobType) {
            case FEISHU_FULL_IMPORT, FEISHU_DEPARTMENT_IMPORT, FEISHU_USER_IMPORT -> SyncBatchType.FEISHU_IMPORT;
            case LDAP_RECONCILE_DEPARTMENT, LDAP_RECONCILE_USER, LDAP_RECONCILE_MEMBERSHIP -> SyncBatchType.LDAP_RECONCILE;
        };
    }

    private SyncSourceType resolveSourceType(SyncJobType jobType) {
        return switch (jobType) {
            case FEISHU_FULL_IMPORT, FEISHU_DEPARTMENT_IMPORT, FEISHU_USER_IMPORT -> SyncSourceType.FEISHU;
            case LDAP_RECONCILE_DEPARTMENT, LDAP_RECONCILE_USER, LDAP_RECONCILE_MEMBERSHIP -> SyncSourceType.SYSTEM;
        };
    }

    private SyncTargetType resolveTargetType(SyncJobType jobType) {
        return switch (jobType) {
            case FEISHU_FULL_IMPORT -> SyncTargetType.IMPORT;
            case FEISHU_DEPARTMENT_IMPORT, LDAP_RECONCILE_DEPARTMENT -> SyncTargetType.DEPARTMENT;
            case FEISHU_USER_IMPORT, LDAP_RECONCILE_USER -> SyncTargetType.USER;
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
