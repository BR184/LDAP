package com.company.idm.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_import_rollback_item")
public class RollbackItemDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long changeItemId;
    private String targetType;
    private String targetKey;
    private String rollbackAction;
    private String restoreJson;
    private String status;
    private String errorMessage;
    private LocalDateTime executedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getChangeItemId() {
        return changeItemId;
    }

    public void setChangeItemId(Long changeItemId) {
        this.changeItemId = changeItemId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetKey() {
        return targetKey;
    }

    public void setTargetKey(String targetKey) {
        this.targetKey = targetKey;
    }

    public String getRollbackAction() {
        return rollbackAction;
    }

    public void setRollbackAction(String rollbackAction) {
        this.rollbackAction = rollbackAction;
    }

    public String getRestoreJson() {
        return restoreJson;
    }

    public void setRestoreJson(String restoreJson) {
        this.restoreJson = restoreJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }
}
