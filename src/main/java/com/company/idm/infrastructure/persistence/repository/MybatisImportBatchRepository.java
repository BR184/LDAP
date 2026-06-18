package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ChangeItemStatus;
import com.company.idm.domain.sync.ChangeType;
import com.company.idm.domain.sync.ImportBatch;
import com.company.idm.domain.sync.ImportBatchRepository;
import com.company.idm.domain.sync.ImportBatchStatus;
import com.company.idm.domain.sync.ImportSourceType;
import com.company.idm.domain.sync.RiskLevel;
import com.company.idm.domain.sync.RollbackAction;
import com.company.idm.domain.sync.RollbackItem;
import com.company.idm.domain.sync.RollbackItemStatus;
import com.company.idm.domain.sync.TargetType;
import com.company.idm.infrastructure.persistence.dataobject.ChangeItemDO;
import com.company.idm.infrastructure.persistence.dataobject.ImportBatchDO;
import com.company.idm.infrastructure.persistence.dataobject.RollbackItemDO;
import com.company.idm.infrastructure.persistence.mapper.ChangeItemMapper;
import com.company.idm.infrastructure.persistence.mapper.ImportBatchMapper;
import com.company.idm.infrastructure.persistence.mapper.RollbackItemMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MybatisImportBatchRepository implements ImportBatchRepository {

    private final ImportBatchMapper importBatchMapper;
    private final ChangeItemMapper changeItemMapper;
    private final RollbackItemMapper rollbackItemMapper;

    @Override
    @Transactional
    public ImportBatch save(ImportBatch batch) {
        ImportBatchDO batchDO = toBatchDO(batch);
        if (batchDO.getId() == null) {
            if (batchDO.getCreatedAt() == null) {
                batchDO.setCreatedAt(LocalDateTime.now());
            }
            importBatchMapper.insert(batchDO);
        } else {
            importBatchMapper.updateById(batchDO);
        }
        Long batchId = batchDO.getId();
        saveChangeItems(batchId, batch.getChangeItems());
        saveRollbackItems(batchId, batch.getRollbackItems());
        return findById(batchId).orElseThrow();
    }

    @Override
    public Optional<ImportBatch> findById(Long id) {
        return Optional.ofNullable(importBatchMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<ImportBatch> findByBatchCode(String batchCode) {
        if (batchCode == null || batchCode.isBlank()) {
            return Optional.empty();
        }
        ImportBatchDO dataObject = importBatchMapper.selectOne(new LambdaQueryWrapper<ImportBatchDO>()
            .eq(ImportBatchDO::getBatchCode, batchCode));
        return Optional.ofNullable(dataObject).map(this::toDomain);
    }

    @Override
    public List<ImportBatch> findRecent(int limit) {
        return importBatchMapper.selectList(new LambdaQueryWrapper<ImportBatchDO>()
                .orderByDesc(ImportBatchDO::getCreatedAt)
                .last("LIMIT " + Math.max(1, limit)))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public boolean existsActiveFeishuImportBatch() {
        List<ImportBatchDO> batches = importBatchMapper.selectList(new LambdaQueryWrapper<ImportBatchDO>()
            .in(ImportBatchDO::getStatus, List.of(
                ImportBatchStatus.DRAFT.name(),
                ImportBatchStatus.CONFIRMED.name(),
                ImportBatchStatus.EXECUTING.name()
            )));
        LocalDateTime now = LocalDateTime.now();
        boolean hasActive = false;
        for (ImportBatchDO batch : batches) {
            if (ImportBatchStatus.DRAFT.name().equals(batch.getStatus())
                && batch.getExpiredAt() != null
                && now.isAfter(batch.getExpiredAt())) {
                batch.setStatus(ImportBatchStatus.EXPIRED.name());
                importBatchMapper.updateById(batch);
                continue;
            }
            hasActive = true;
        }
        return hasActive;
    }

    private void saveChangeItems(Long batchId, List<ChangeItem> items) {
        if (items == null) {
            return;
        }
        for (ChangeItem item : items) {
            ChangeItemDO dataObject = toChangeItemDO(item.withBatchId(batchId));
            if (dataObject.getId() == null) {
                changeItemMapper.insert(dataObject);
            } else {
                changeItemMapper.updateById(dataObject);
            }
        }
    }

    private void saveRollbackItems(Long batchId, List<RollbackItem> items) {
        if (items == null) {
            return;
        }
        for (RollbackItem item : items) {
            RollbackItemDO dataObject = toRollbackItemDO(item, batchId);
            if (dataObject.getId() == null) {
                rollbackItemMapper.insert(dataObject);
            } else {
                rollbackItemMapper.updateById(dataObject);
            }
        }
    }

    private ImportBatch toDomain(ImportBatchDO dataObject) {
        List<ChangeItem> changeItems = changeItemMapper.selectList(new LambdaQueryWrapper<ChangeItemDO>()
                .eq(ChangeItemDO::getBatchId, dataObject.getId())
                .orderByAsc(ChangeItemDO::getId))
            .stream()
            .map(this::toChangeItem)
            .toList();
        List<RollbackItem> rollbackItems = rollbackItemMapper.selectList(new LambdaQueryWrapper<RollbackItemDO>()
                .eq(RollbackItemDO::getBatchId, dataObject.getId())
                .orderByAsc(RollbackItemDO::getId))
            .stream()
            .map(this::toRollbackItem)
            .toList();
        return ImportBatch.builder()
            .id(dataObject.getId())
            .batchCode(dataObject.getBatchCode())
            .fileName(dataObject.getFileName())
            .fileHash(dataObject.getFileHash())
            .sourceType(ImportSourceType.valueOf(dataObject.getSourceType()))
            .changeItems(changeItems)
            .rollbackItems(rollbackItems)
            .totalItems(nullToZero(dataObject.getTotalItems()))
            .enabledItems(nullToZero(dataObject.getEnabledItems()))
            .conflictItems(nullToZero(dataObject.getConflictItems()))
            .status(ImportBatchStatus.valueOf(dataObject.getStatus()))
            .createdBy(dataObject.getCreatedBy())
            .createdAt(dataObject.getCreatedAt())
            .confirmedBy(dataObject.getConfirmedBy())
            .confirmedAt(dataObject.getConfirmedAt())
            .executedBy(dataObject.getExecutedBy())
            .executedAt(dataObject.getExecutedAt())
            .rollbackBy(dataObject.getRollbackBy())
            .rollbackAt(dataObject.getRollbackAt())
            .expiredAt(dataObject.getExpiredAt())
            .remark(dataObject.getRemark())
            .build();
    }

    private ImportBatchDO toBatchDO(ImportBatch batch) {
        ImportBatchDO dataObject = new ImportBatchDO();
        dataObject.setId(batch.getId());
        dataObject.setBatchCode(batch.getBatchCode());
        dataObject.setFileName(batch.getFileName());
        dataObject.setFileHash(batch.getFileHash());
        dataObject.setSourceType(batch.getSourceType().name());
        dataObject.setTotalItems(batch.getTotalItems());
        dataObject.setEnabledItems(batch.getEnabledItems());
        dataObject.setConflictItems(batch.getConflictItems());
        dataObject.setStatus(batch.getStatus().name());
        dataObject.setCreatedBy(batch.getCreatedBy());
        dataObject.setCreatedAt(batch.getCreatedAt());
        dataObject.setConfirmedBy(batch.getConfirmedBy());
        dataObject.setConfirmedAt(batch.getConfirmedAt());
        dataObject.setExecutedBy(batch.getExecutedBy());
        dataObject.setExecutedAt(batch.getExecutedAt());
        dataObject.setRollbackBy(batch.getRollbackBy());
        dataObject.setRollbackAt(batch.getRollbackAt());
        dataObject.setExpiredAt(batch.getExpiredAt());
        dataObject.setRemark(batch.getRemark());
        return dataObject;
    }

    private ChangeItem toChangeItem(ChangeItemDO dataObject) {
        return ChangeItem.builder()
            .id(dataObject.getId())
            .batchId(dataObject.getBatchId())
            .targetType(TargetType.valueOf(dataObject.getTargetType()))
            .targetKey(dataObject.getTargetKey())
            .changeType(ChangeType.valueOf(dataObject.getChangeType()))
            .fieldName(dataObject.getFieldName())
            .beforeValue(dataObject.getBeforeValue())
            .afterValue(dataObject.getAfterValue())
            .beforeJson(dataObject.getBeforeJson())
            .afterJson(dataObject.getAfterJson())
            .objectVersion(dataObject.getObjectVersion())
            .defaultEnabled(Boolean.TRUE.equals(dataObject.getDefaultEnabled()))
            .enabled(Boolean.TRUE.equals(dataObject.getEnabled()))
            .requiresConfirmation(Boolean.TRUE.equals(dataObject.getRequiresConfirmation()))
            .confirmed(Boolean.TRUE.equals(dataObject.getConfirmed()))
            .riskLevel(RiskLevel.valueOf(dataObject.getRiskLevel()))
            .blockReason(dataObject.getBlockReason())
            .status(ChangeItemStatus.valueOf(dataObject.getStatus()))
            .errorMessage(dataObject.getErrorMessage())
            .retryCount(nullToZero(dataObject.getRetryCount()))
            .executedAt(dataObject.getExecutedAt())
            .build();
    }

    private ChangeItemDO toChangeItemDO(ChangeItem item) {
        ChangeItemDO dataObject = new ChangeItemDO();
        dataObject.setId(item.getId());
        dataObject.setBatchId(item.getBatchId());
        dataObject.setTargetType(item.getTargetType().name());
        dataObject.setTargetKey(item.getTargetKey());
        dataObject.setChangeType(item.getChangeType().name());
        dataObject.setFieldName(item.getFieldName());
        dataObject.setBeforeValue(item.getBeforeValue());
        dataObject.setAfterValue(item.getAfterValue());
        dataObject.setBeforeJson(item.getBeforeJson());
        dataObject.setAfterJson(item.getAfterJson());
        dataObject.setObjectVersion(item.getObjectVersion());
        dataObject.setDefaultEnabled(item.isDefaultEnabled());
        dataObject.setEnabled(item.isEnabled());
        dataObject.setRequiresConfirmation(item.isRequiresConfirmation());
        dataObject.setConfirmed(item.isConfirmed());
        dataObject.setRiskLevel(item.getRiskLevel().name());
        dataObject.setBlockReason(item.getBlockReason());
        dataObject.setStatus(item.getStatus().name());
        dataObject.setErrorMessage(item.getErrorMessage());
        dataObject.setRetryCount(item.getRetryCount());
        dataObject.setExecutedAt(item.getExecutedAt());
        return dataObject;
    }

    private RollbackItem toRollbackItem(RollbackItemDO dataObject) {
        return RollbackItem.builder()
            .id(dataObject.getId())
            .batchId(dataObject.getBatchId())
            .changeItemId(dataObject.getChangeItemId())
            .targetType(TargetType.valueOf(dataObject.getTargetType()))
            .targetKey(dataObject.getTargetKey())
            .rollbackAction(RollbackAction.valueOf(dataObject.getRollbackAction()))
            .restoreJson(dataObject.getRestoreJson())
            .status(RollbackItemStatus.valueOf(dataObject.getStatus()))
            .errorMessage(dataObject.getErrorMessage())
            .executedAt(dataObject.getExecutedAt())
            .build();
    }

    private RollbackItemDO toRollbackItemDO(RollbackItem item, Long batchId) {
        RollbackItemDO dataObject = new RollbackItemDO();
        dataObject.setId(item.getId());
        dataObject.setBatchId(batchId);
        dataObject.setChangeItemId(item.getChangeItemId());
        dataObject.setTargetType(item.getTargetType().name());
        dataObject.setTargetKey(item.getTargetKey());
        dataObject.setRollbackAction(item.getRollbackAction().name());
        dataObject.setRestoreJson(item.getRestoreJson());
        dataObject.setStatus(item.getStatus().name());
        dataObject.setErrorMessage(item.getErrorMessage());
        dataObject.setExecutedAt(item.getExecutedAt());
        return dataObject;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
