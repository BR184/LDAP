package com.company.idm.domain.sync;

import com.company.idm.common.exception.BizException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class ChangeItem {

    private Long id;
    private Long batchId;
    private TargetType targetType;
    private String targetKey;
    private ChangeType changeType;
    private ImportFieldKey fieldKey;
    private String beforeValue;
    private String afterValue;
    private String beforeJson;
    private String afterJson;
    private Integer objectVersion;
    private boolean defaultEnabled;
    private boolean enabled;
    private boolean requiresConfirmation;
    private boolean confirmed;
    private RiskLevel riskLevel;
    private String blockReason;
    private ImportConflictCode conflictCode;
    private ChangeItemStatus status;
    private String errorMessage;
    private int retryCount;
    private LocalDateTime executedAt;
    private ConflictResolutionAction resolutionAction;
    private String resolvedBy;
    private LocalDateTime resolvedAt;

    public ChangeItem withBatchId(Long batchId) {
        this.batchId = batchId;
        return this;
    }

    public void setEnabled(boolean enabled) {
        if (riskLevel == RiskLevel.BLOCKER && enabled) {
            enabled = false;
        }
        this.enabled = enabled;
    }

    public void confirm() {
        if (requiresConfirmation) {
            this.confirmed = true;
        }
    }

    public void resolveBySkipping(String resolvedBy) {
        if (changeType != ChangeType.CONFLICT || riskLevel != RiskLevel.BLOCKER || status != ChangeItemStatus.PENDING) {
            throw new BizException("IMPORT_CONFLICT_NOT_RESOLVABLE", "当前变更项不是可处理的阻断冲突");
        }
        resolve(ConflictResolutionAction.SKIP_RELATED_CHANGES, resolvedBy);
    }

    public void resolveByMerging(String resolvedBy) {
        if (!canMergeByEmployeeNo()) {
            throw new BizException("IMPORT_CONFLICT_MERGE_NOT_ALLOWED", "当前冲突不允许按工号合并更新");
        }
        resolve(ConflictResolutionAction.MERGE_BY_EMPLOYEE_NO, resolvedBy);
    }

    public boolean canMergeByEmployeeNo() {
        return changeType == ChangeType.CONFLICT
            && riskLevel == RiskLevel.BLOCKER
            && status == ChangeItemStatus.PENDING
            && conflictCode == ImportConflictCode.EMPLOYEE_NO_OWNED_BY_ANOTHER_USER;
    }

    public void restoreMergeCandidateSnapshots(String beforeJson, String afterJson) {
        if (!canMergeByEmployeeNo() || beforeJson == null || beforeJson.isBlank() || afterJson == null || afterJson.isBlank()) {
            throw new BizException("IMPORT_CONFLICT_CANDIDATE_INVALID", "恢复的冲突候选资料不完整");
        }
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
    }

    public List<ConflictResolutionAction> availableResolutionActions() {
        if (changeType != ChangeType.CONFLICT || riskLevel != RiskLevel.BLOCKER || status != ChangeItemStatus.PENDING) {
            return List.of();
        }
        List<ConflictResolutionAction> actions = new ArrayList<>();
        if (canMergeByEmployeeNo()) {
            actions.add(ConflictResolutionAction.MERGE_BY_EMPLOYEE_NO);
        }
        actions.add(ConflictResolutionAction.SKIP_RELATED_CHANGES);
        return List.copyOf(actions);
    }

    public void skipDueToConflict() {
        if (status != ChangeItemStatus.PENDING || changeType == ChangeType.CONFLICT) {
            return;
        }
        this.enabled = false;
        this.status = ChangeItemStatus.SKIPPED;
    }

    public boolean isPendingBlocker() {
        return riskLevel == RiskLevel.BLOCKER && status == ChangeItemStatus.PENDING;
    }

    private void resolve(ConflictResolutionAction action, String operator) {
        this.enabled = false;
        this.confirmed = true;
        this.status = ChangeItemStatus.SKIPPED;
        this.resolutionAction = action;
        this.resolvedBy = operator;
        this.resolvedAt = LocalDateTime.now();
    }

    public void markExecuted() {
        this.status = ChangeItemStatus.EXECUTED;
        this.errorMessage = null;
        this.executedAt = LocalDateTime.now();
    }

    public void markSkipped() {
        this.status = ChangeItemStatus.SKIPPED;
    }

    public void markFailed(String errorMessage) {
        this.status = ChangeItemStatus.FAILED;
        this.errorMessage = errorMessage;
        this.executedAt = LocalDateTime.now();
    }

    public void markLdapFailed(String errorMessage) {
        this.status = ChangeItemStatus.LDAP_FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
        this.executedAt = LocalDateTime.now();
    }

    public void markRolledBack() {
        this.status = ChangeItemStatus.ROLLED_BACK;
    }

    public boolean canRetry() {
        return status == ChangeItemStatus.LDAP_FAILED && retryCount < 3;
    }

    public void resetForRetry() {
        if (!canRetry()) {
            return;
        }
        this.status = ChangeItemStatus.PENDING;
        this.errorMessage = null;
    }

    public boolean executable() {
        return enabled && riskLevel != RiskLevel.BLOCKER && status == ChangeItemStatus.PENDING;
    }
}
