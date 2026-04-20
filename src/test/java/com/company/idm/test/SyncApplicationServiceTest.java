package com.company.idm.test;

import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.application.sync.SyncBatchDetail;
import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.SyncJobHandler;
import com.company.idm.common.enums.SyncBatchType;
import com.company.idm.common.enums.SyncJobType;
import com.company.idm.common.enums.SyncRunStatus;
import com.company.idm.common.enums.SyncTargetType;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.sync.SyncBatch;
import com.company.idm.domain.sync.SyncBatchRepository;
import com.company.idm.domain.sync.SyncDiff;
import com.company.idm.domain.sync.SyncDiffRepository;
import com.company.idm.domain.sync.SyncJob;
import com.company.idm.domain.sync.SyncJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证同步应用服务编排逻辑的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class SyncApplicationServiceTest {

    @Mock
    private SyncBatchRepository syncBatchRepository;

    @Mock
    private SyncJobRepository syncJobRepository;

    @Mock
    private SyncDiffRepository syncDiffRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    void shouldCreateFeishuPreviewBatchAndJobs() {
        List<SyncJobHandler> handlers = List.of(
            new SuccessHandler(SyncJobType.FEISHU_DEPARTMENT_IMPORT, SyncTargetType.DEPARTMENT),
            new SuccessHandler(SyncJobType.FEISHU_USER_IMPORT, SyncTargetType.USER)
        );
        SyncApplicationService service = new SyncApplicationService(
            handlers,
            syncBatchRepository,
            syncJobRepository,
            syncDiffRepository,
            auditLogRepository,
            new ObjectMapper()
        );

        List<SyncJob> storedJobs = new ArrayList<>();
        List<SyncDiff> storedDiffs = new ArrayList<>();
        AtomicLong batchId = new AtomicLong(1L);
        AtomicLong jobId = new AtomicLong(1L);

        when(syncBatchRepository.existsRunningBatch(SyncBatchType.FEISHU_IMPORT)).thenReturn(false);
        when(syncBatchRepository.save(any(SyncBatch.class))).thenAnswer(invocation -> {
            SyncBatch batch = invocation.getArgument(0);
            return batch.getId() == null ? batch.toBuilder().id(batchId.getAndIncrement()).build() : batch;
        });
        when(syncJobRepository.save(any(SyncJob.class))).thenAnswer(invocation -> {
            SyncJob job = invocation.getArgument(0);
            SyncJob saved = job.getId() == null ? job.toBuilder().id(jobId.getAndIncrement()).build() : job;
            storedJobs.removeIf(item -> item.getId().equals(saved.getId()));
            storedJobs.add(saved);
            return saved;
        });
        when(syncDiffRepository.findByBatchNo(any())).thenAnswer(invocation ->
            storedDiffs.stream().filter(item -> item.getBatchNo().equals(invocation.getArgument(0))).toList()
        );
        when(syncJobRepository.findByBatchNo(any())).thenAnswer(invocation ->
            storedJobs.stream().filter(item -> item.getBatchNo().equals(invocation.getArgument(0))).toList()
        );

        SyncBatchDetail detail = service.executeFeishuUserSync("admin", SyncTriggerMode.MANUAL);

        assertThat(detail.batch().getBatchType()).isEqualTo(SyncBatchType.FEISHU_IMPORT);
        assertThat(detail.batch().getStatus()).isEqualTo(SyncRunStatus.SUCCESS);
        assertThat(detail.batch().getFileName()).isNull();
        assertThat(detail.jobs()).hasSize(2);
        assertThat(detail.diffs()).isEmpty();
        verify(auditLogRepository).save(any());
    }

    @Test
    void shouldRejectWhenSameBatchTypeIsRunning() {
        SyncApplicationService service = new SyncApplicationService(
            List.of(
                new SuccessHandler(SyncJobType.FEISHU_DEPARTMENT_IMPORT, SyncTargetType.DEPARTMENT),
                new SuccessHandler(SyncJobType.FEISHU_USER_IMPORT, SyncTargetType.USER)
            ),
            syncBatchRepository,
            syncJobRepository,
            syncDiffRepository,
            auditLogRepository,
            new ObjectMapper()
        );
        when(syncBatchRepository.existsRunningBatch(SyncBatchType.FEISHU_IMPORT)).thenReturn(true);

        assertThatThrownBy(() -> service.executeFeishuUserSync("admin", SyncTriggerMode.MANUAL))
            .isInstanceOf(BizException.class)
            .hasMessage("当前已有同类型同步任务正在执行");
    }

    @Test
    void shouldCreateManualFileImportBatchWithDocumentPath() {
        List<SyncJobHandler> handlers = List.of(
            new SuccessHandler(SyncJobType.FEISHU_DEPARTMENT_IMPORT, SyncTargetType.DEPARTMENT)
        );
        SyncApplicationService service = new SyncApplicationService(
            handlers,
            syncBatchRepository,
            syncJobRepository,
            syncDiffRepository,
            auditLogRepository,
            new ObjectMapper()
        );

        List<SyncJob> storedJobs = new ArrayList<>();
        List<SyncDiff> storedDiffs = new ArrayList<>();
        AtomicLong batchId = new AtomicLong(1L);
        AtomicLong jobId = new AtomicLong(1L);

        when(syncBatchRepository.existsRunningBatch(SyncBatchType.FEISHU_IMPORT)).thenReturn(false);
        when(syncBatchRepository.save(any(SyncBatch.class))).thenAnswer(invocation -> {
            SyncBatch batch = invocation.getArgument(0);
            return batch.getId() == null ? batch.toBuilder().id(batchId.getAndIncrement()).build() : batch;
        });
        when(syncJobRepository.save(any(SyncJob.class))).thenAnswer(invocation -> {
            SyncJob job = invocation.getArgument(0);
            SyncJob saved = job.getId() == null ? job.toBuilder().id(jobId.getAndIncrement()).build() : job;
            storedJobs.removeIf(item -> item.getId().equals(saved.getId()));
            storedJobs.add(saved);
            return saved;
        });
        when(syncDiffRepository.findByBatchNo(any())).thenAnswer(invocation ->
            storedDiffs.stream().filter(item -> item.getBatchNo().equals(invocation.getArgument(0))).toList()
        );
        when(syncJobRepository.findByBatchNo(any())).thenAnswer(invocation ->
            storedJobs.stream().filter(item -> item.getBatchNo().equals(invocation.getArgument(0))).toList()
        );

        SyncBatchDetail detail = service.executeFeishuDepartmentFileImport(
            "departments/demo.json",
            false,
            "manual file import",
            "admin",
            SyncTriggerMode.MANUAL
        );

        assertThat(detail.batch().getFileName()).isEqualTo("departments/demo.json");
        assertThat(detail.jobs()).singleElement().satisfies(job ->
            assertThat(job.getRequestJson()).contains("departments/demo.json")
        );
    }

    private static class SuccessHandler implements SyncJobHandler {

        private final SyncJobType jobType;
        private final SyncTargetType targetType;

        private SuccessHandler(SyncJobType jobType, SyncTargetType targetType) {
            this.jobType = jobType;
            this.targetType = targetType;
        }

        @Override
        public SyncJobType jobType() {
            return jobType;
        }

        @Override
        public com.company.idm.application.sync.SyncJobExecutionResult preview(com.company.idm.application.sync.SyncRequestPayload payload) {
            return new com.company.idm.application.sync.SyncJobExecutionResult(
                SyncRunStatus.SUCCESS,
                "{\"ok\":true}",
                null,
                List.<SyncDiffPayload>of()
            );
        }

        @Override
        public com.company.idm.application.sync.SyncJobExecutionResult execute(com.company.idm.application.sync.SyncRequestPayload payload) {
            return new com.company.idm.application.sync.SyncJobExecutionResult(
                SyncRunStatus.SUCCESS,
                "{\"ok\":true,\"target\":\"" + targetType.name() + "\"}",
                null,
                List.<SyncDiffPayload>of()
            );
        }
    }
}
