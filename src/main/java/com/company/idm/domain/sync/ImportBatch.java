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
public class ImportBatch {

    private Long id;
    private String batchCode;
    private String fileName;
    private String fileHash;
    private ImportSourceType sourceType;
    @Builder.Default
    private List<ChangeItem> changeItems = new ArrayList<>();
    @Builder.Default
    private List<RollbackItem> rollbackItems = new ArrayList<>();
    private int totalItems;
    private int enabledItems;
    private int conflictItems;
    private ImportBatchStatus status;
    private String createdBy;
    private LocalDateTime createdAt;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private String executedBy;
    private LocalDateTime executedAt;
    private String rollbackBy;
    private LocalDateTime rollbackAt;
    private LocalDateTime expiredAt;
    private String remark;

    public void addChangeItem(ChangeItem item) {
        if (status != ImportBatchStatus.DRAFT) {
            throw new BizException("IMPORT_BATCH_NOT_DRAFT", "导入计划已确认，无法继续添加变更项");
        }
        changeItems.add(item);
        recalculateStatistics();
    }

    public void applyConfirmations(List<Long> enabledItemIds, List<Long> confirmedItemIds) {
        for (ChangeItem item : changeItems) {
            if (item.getId() == null) {
                continue;
            }
            if (enabledItemIds != null) {
                item.setEnabled(enabledItemIds.contains(item.getId()));
            }
            if (confirmedItemIds != null && confirmedItemIds.contains(item.getId())) {
                item.confirm();
            }
        }
        recalculateStatistics();
    }

    public void confirm(String confirmedBy) {
        if (status != ImportBatchStatus.DRAFT) {
            throw new BizException("IMPORT_BATCH_STATUS_INVALID", "只有草稿状态的导入计划可以确认");
        }
        if (isExpired()) {
            throw new BizException("IMPORT_BATCH_EXPIRED", "导入计划已过期，请重新生成");
        }
        if (hasBlockerConflicts()) {
            throw new BizException("IMPORT_BATCH_HAS_BLOCKER", "存在阻断级冲突，无法确认");
        }
        if (hasUnconfirmedItems()) {
            throw new BizException("IMPORT_BATCH_HAS_UNCONFIRMED_ITEMS", "存在未确认的高风险变更项");
        }
        this.status = ImportBatchStatus.CONFIRMED;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = LocalDateTime.now();
    }

    public void execute(String executedBy) {
        if (status != ImportBatchStatus.CONFIRMED) {
            throw new BizException("IMPORT_BATCH_NOT_CONFIRMED", "导入计划未确认，无法执行");
        }
        if (isExpired()) {
            throw new BizException("IMPORT_BATCH_EXPIRED", "导入计划已过期，请重新生成");
        }
        this.status = ImportBatchStatus.EXECUTING;
        this.executedBy = executedBy;
        this.executedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = changeItems.stream().anyMatch(item ->
            item.getStatus() == ChangeItemStatus.FAILED || item.getStatus() == ChangeItemStatus.LDAP_FAILED)
            ? ImportBatchStatus.FAILED
            : ImportBatchStatus.COMPLETED;
    }

    public void expire() {
        if (status == ImportBatchStatus.DRAFT && isExpired()) {
            this.status = ImportBatchStatus.EXPIRED;
        }
    }

    public void prepareRetry() {
        if (status != ImportBatchStatus.FAILED) {
            throw new BizException("IMPORT_BATCH_STATUS_INVALID", "只有失败批次可以重试");
        }
        boolean hasRetryableItem = false;
        for (ChangeItem item : changeItems) {
            if (item.canRetry()) {
                item.resetForRetry();
                hasRetryableItem = true;
            }
        }
        if (!hasRetryableItem) {
            throw new BizException("IMPORT_BATCH_NO_RETRYABLE_ITEM", "当前批次没有可重试的 LDAP 失败项");
        }
        this.status = ImportBatchStatus.CONFIRMED;
    }

    public void markRolledBack(String rollbackBy) {
        if (!canRollback()) {
            throw new BizException("IMPORT_BATCH_CANNOT_ROLLBACK", "当前批次不允许撤回");
        }
        this.status = ImportBatchStatus.ROLLED_BACK;
        this.rollbackBy = rollbackBy;
        this.rollbackAt = LocalDateTime.now();
    }

    public boolean canRollback() {
        return status == ImportBatchStatus.COMPLETED && !hasLdapFailedItems();
    }

    public List<ChangeItem> getEnabledChangeItems() {
        return changeItems.stream().filter(ChangeItem::executable).toList();
    }

    public List<ChangeItem> getExecutedChangeItems() {
        return changeItems.stream()
            .filter(item -> item.getStatus() == ChangeItemStatus.EXECUTED)
            .toList();
    }

    public boolean isExpired() {
        return expiredAt != null && LocalDateTime.now().isAfter(expiredAt);
    }

    private boolean hasBlockerConflicts() {
        return changeItems.stream().anyMatch(item -> item.getRiskLevel() == RiskLevel.BLOCKER);
    }

    private boolean hasUnconfirmedItems() {
        return changeItems.stream()
            .anyMatch(item -> item.isEnabled() && item.isRequiresConfirmation() && !item.isConfirmed());
    }

    private boolean hasLdapFailedItems() {
        return changeItems.stream().anyMatch(item -> item.getStatus() == ChangeItemStatus.LDAP_FAILED);
    }

    private void recalculateStatistics() {
        this.totalItems = changeItems.size();
        this.enabledItems = (int) changeItems.stream().filter(ChangeItem::isEnabled).count();
        this.conflictItems = (int) changeItems.stream()
            .filter(item -> item.getChangeType() == ChangeType.CONFLICT || item.getRiskLevel() == RiskLevel.BLOCKER)
            .count();
    }
}
