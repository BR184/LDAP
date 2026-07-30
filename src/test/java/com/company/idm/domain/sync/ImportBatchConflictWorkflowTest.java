package com.company.idm.domain.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.idm.common.exception.BizException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImportBatchConflictWorkflowTest {

    @Test
    void excludesRelatedChangesBeforeResolvingBlockerAndAllowsConfirmation() {
        ChangeItem blocker = blocker(10L, "1941");
        ChangeItem relatedCreate = pendingCreate(11L, "zhangsan");
        ImportBatch batch = draftBatch(List.of(blocker, relatedCreate));

        batch.resolveConflictBySkipping(10L, List.of(11L), "admin");
        batch.confirm("admin");

        assertThat(blocker.getStatus()).isEqualTo(ChangeItemStatus.SKIPPED);
        assertThat(blocker.getResolutionAction()).isEqualTo(ConflictResolutionAction.SKIP_RELATED_CHANGES);
        assertThat(blocker.getResolvedBy()).isEqualTo("admin");
        assertThat(blocker.getResolvedAt()).isNotNull();
        assertThat(relatedCreate.isEnabled()).isFalse();
        assertThat(relatedCreate.getStatus()).isEqualTo(ChangeItemStatus.SKIPPED);
        assertThat(batch.getConflictItems()).isZero();
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.CONFIRMED);
    }

    @Test
    void pendingBlockerStillPreventsConfirmation() {
        ImportBatch batch = draftBatch(List.of(blocker(10L, "1941"), pendingCreate(11L, "zhangsan")));

        assertThatThrownBy(() -> batch.confirm("admin"))
            .isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("IMPORT_BATCH_HAS_BLOCKER"));
    }

    @Test
    void mergesEmployeeNumberConflictIntoExecutableUpdatesAndAllowsConfirmation() {
        ChangeItem blocker = blocker(10L, "zhangsan").toBuilder()
            .conflictCode(ImportConflictCode.EMPLOYEE_NO_OWNED_BY_ANOTHER_USER)
            .afterJson("candidate-json")
            .build();
        ChangeItem staleCreate = pendingCreate(11L, "zhangsan");
        ChangeItem nameUpdate = pendingUpdate("yangchenyu", "realName");
        ImportBatch batch = draftBatch(List.of(blocker, staleCreate));

        batch.resolveConflictByMerging(
            10L,
            List.of(11L),
            List.of(nameUpdate),
            "admin"
        );
        batch.confirm("admin");

        assertThat(blocker.getStatus()).isEqualTo(ChangeItemStatus.SKIPPED);
        assertThat(blocker.getResolutionAction()).isEqualTo(ConflictResolutionAction.MERGE_BY_EMPLOYEE_NO);
        assertThat(staleCreate.getStatus()).isEqualTo(ChangeItemStatus.SKIPPED);
        assertThat(batch.getChangeItems()).contains(nameUpdate);
        assertThat(nameUpdate.isEnabled()).isTrue();
        assertThat(batch.getConflictItems()).isZero();
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.CONFIRMED);
    }

    @Test
    void offersEmployeeNumberMergeForLegacyConflictWithoutCandidateSnapshot() {
        ChangeItem blocker = blocker(10L, "zhangsan").toBuilder()
            .conflictCode(ImportConflictCode.EMPLOYEE_NO_OWNED_BY_ANOTHER_USER)
            .afterJson(null)
            .build();

        assertThat(blocker.availableResolutionActions()).containsExactly(
            ConflictResolutionAction.MERGE_BY_EMPLOYEE_NO,
            ConflictResolutionAction.SKIP_RELATED_CHANGES
        );
    }

    @Test
    void cancelsDraftAndRecordsAuditIdentity() {
        ImportBatch batch = draftBatch(List.of(pendingCreate(11L, "zhangsan")));

        batch.cancel("admin");

        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.CANCELLED);
        assertThat(batch.getCancelledBy()).isEqualTo("admin");
        assertThat(batch.getCancelledAt()).isNotNull();
    }

    @Test
    void refusesToCancelConfirmedBatch() {
        ImportBatch batch = draftBatch(List.of(pendingCreate(11L, "zhangsan")));
        batch.confirm("admin");

        assertThatThrownBy(() -> batch.cancel("admin"))
            .isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("IMPORT_BATCH_CANNOT_CANCEL"));
    }

    private ImportBatch draftBatch(List<ChangeItem> items) {
        return ImportBatch.builder()
            .id(1L)
            .sourceType(ImportSourceType.FEISHU_EXPORT)
            .status(ImportBatchStatus.DRAFT)
            .changeItems(items)
            .expiredAt(LocalDateTime.now().plusHours(1))
            .build();
    }

    private ChangeItem blocker(Long id, String targetKey) {
        return ChangeItem.builder()
            .id(id)
            .targetType(TargetType.USER)
            .targetKey(targetKey)
            .changeType(ChangeType.CONFLICT)
            .enabled(false)
            .requiresConfirmation(true)
            .riskLevel(RiskLevel.BLOCKER)
            .status(ChangeItemStatus.PENDING)
            .build();
    }

    private ChangeItem pendingCreate(Long id, String targetKey) {
        return ChangeItem.builder()
            .id(id)
            .targetType(TargetType.USER)
            .targetKey(targetKey)
            .changeType(ChangeType.CREATE)
            .enabled(true)
            .riskLevel(RiskLevel.MEDIUM)
            .status(ChangeItemStatus.PENDING)
            .build();
    }

    private ChangeItem pendingUpdate(String targetKey, String ignoredFieldName) {
        return ChangeItem.builder()
            .targetType(TargetType.USER)
            .targetKey(targetKey)
            .changeType(ChangeType.UPDATE)
            .fieldKey(ImportFieldKey.USER_REAL_NAME)
            .enabled(true)
            .confirmed(true)
            .riskLevel(RiskLevel.LOW)
            .status(ChangeItemStatus.PENDING)
            .build();
    }
}
