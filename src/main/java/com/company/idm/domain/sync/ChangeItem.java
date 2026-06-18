package com.company.idm.domain.sync;

import java.time.LocalDateTime;
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
    private String fieldName;
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
    private ChangeItemStatus status;
    private String errorMessage;
    private int retryCount;
    private LocalDateTime executedAt;

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
