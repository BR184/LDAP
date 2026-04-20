package com.company.idm.test;

import com.company.idm.application.sync.SyncBatchDetail;
import com.company.idm.common.enums.SyncBatchType;
import com.company.idm.common.enums.SyncDiffStatus;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncJobType;
import com.company.idm.common.enums.SyncRunStatus;
import com.company.idm.common.enums.SyncSourceType;
import com.company.idm.common.enums.SyncTargetType;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.domain.sync.SyncBatch;
import com.company.idm.domain.sync.SyncDiff;
import com.company.idm.domain.sync.SyncJob;
import com.company.idm.interfaces.sync.SyncBatchDetailResponse;
import com.company.idm.interfaces.sync.SyncResponseAssembler;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证同步响应组装器的单元测试。
 */
class SyncResponseAssemblerTest {

    @Test
    void shouldAssembleBatchJobAndDiffResponses() {
        SyncBatch batch = SyncBatch.builder()
            .id(1L)
            .batchNo("BATCH-001")
            .batchType(SyncBatchType.FEISHU_IMPORT)
            .sourceType(SyncSourceType.FEISHU)
            .triggerMode(SyncTriggerMode.MANUAL)
            .status(SyncRunStatus.SUCCESS)
            .fileName("ignored.json")
            .fileHash("abc123")
            .summaryJson("{\"ok\":true}")
            .operator("admin")
            .correlationBatchNo("CORR-001")
            .build();
        SyncJob job = SyncJob.builder()
            .id(2L)
            .batchNo("BATCH-001")
            .jobType(SyncJobType.FEISHU_USER_IMPORT)
            .targetType(SyncTargetType.USER)
            .status(SyncRunStatus.SUCCESS)
            .requestJson("{\"force\":false}")
            .resultJson("{\"imported\":1}")
            .errorMessage(null)
            .operator("admin")
            .retryCount(0)
            .build();
        SyncDiff diff = SyncDiff.builder()
            .id(3L)
            .batchNo("BATCH-001")
            .jobId(2L)
            .targetType(SyncTargetType.USER)
            .targetKey("alice")
            .diffType(SyncDiffType.MISSING_IN_LDAP)
            .sourceSnapshot("{\"username\":\"alice\"}")
            .targetSnapshot("{}")
            .repairable(1)
            .status(SyncDiffStatus.REPAIRED)
            .build();
        SyncResponseAssembler assembler = new SyncResponseAssembler();

        SyncBatchDetailResponse response = assembler.toResponse(new SyncBatchDetail(batch, List.of(job), List.of(diff)));

        assertThat(response.batch().batchNo()).isEqualTo("BATCH-001");
        assertThat(response.batch().batchType()).isEqualTo("FEISHU_IMPORT");
        assertThat(response.jobs()).singleElement().satisfies(item -> {
            assertThat(item.jobType()).isEqualTo("FEISHU_USER_IMPORT");
            assertThat(item.targetType()).isEqualTo("USER");
            assertThat(item.status()).isEqualTo("SUCCESS");
        });
        assertThat(response.diffs()).singleElement().satisfies(item -> {
            assertThat(item.diffType()).isEqualTo("MISSING_IN_LDAP");
            assertThat(item.targetKey()).isEqualTo("alice");
            assertThat(item.status()).isEqualTo("REPAIRED");
        });
    }
}
