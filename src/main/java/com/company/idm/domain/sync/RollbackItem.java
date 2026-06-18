package com.company.idm.domain.sync;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class RollbackItem {

    private Long id;
    private Long batchId;
    private Long changeItemId;
    private TargetType targetType;
    private String targetKey;
    private RollbackAction rollbackAction;
    private String restoreJson;
    private RollbackItemStatus status;
    private String errorMessage;
    private LocalDateTime executedAt;

    public void markExecuted() {
        this.status = RollbackItemStatus.EXECUTED;
        this.errorMessage = null;
        this.executedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = RollbackItemStatus.FAILED;
        this.errorMessage = errorMessage;
        this.executedAt = LocalDateTime.now();
    }
}
