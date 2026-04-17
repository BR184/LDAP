package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.common.enums.SyncBatchType;
import com.company.idm.common.enums.SyncRunStatus;
import com.company.idm.common.enums.SyncSourceType;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.domain.sync.SyncBatch;
import com.company.idm.domain.sync.SyncBatchRepository;
import com.company.idm.infrastructure.persistence.dataobject.SyncBatchDO;
import com.company.idm.infrastructure.persistence.mapper.SyncBatchMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 实现同步批次仓储。
 */
@Repository
@RequiredArgsConstructor
public class MybatisSyncBatchRepository implements SyncBatchRepository {

    private final SyncBatchMapper syncBatchMapper;

    @Override
    public SyncBatch save(SyncBatch batch) {
        SyncBatchDO dataObject = toDataObject(batch);
        if (dataObject.getId() == null) {
            dataObject.setGmtCreate(LocalDateTime.now());
            dataObject.setGmtModified(LocalDateTime.now());
            syncBatchMapper.insert(dataObject);
        } else {
            dataObject.setGmtModified(LocalDateTime.now());
            syncBatchMapper.updateById(dataObject);
        }
        return toDomain(dataObject);
    }

    @Override
    public Optional<SyncBatch> findByBatchNo(String batchNo) {
        return Optional.ofNullable(syncBatchMapper.selectOne(new LambdaQueryWrapper<SyncBatchDO>()
            .eq(SyncBatchDO::getBatchNo, batchNo)))
            .map(this::toDomain);
    }

    @Override
    public boolean existsRunningBatch(SyncBatchType batchType) {
        return syncBatchMapper.selectCount(new LambdaQueryWrapper<SyncBatchDO>()
            .eq(SyncBatchDO::getBatchType, batchType.name())
            .eq(SyncBatchDO::getStatus, SyncRunStatus.RUNNING.name())) > 0;
    }

    private SyncBatch toDomain(SyncBatchDO dataObject) {
        return SyncBatch.builder()
            .id(dataObject.getId())
            .batchNo(dataObject.getBatchNo())
            .batchType(SyncBatchType.valueOf(dataObject.getBatchType()))
            .sourceType(SyncSourceType.valueOf(dataObject.getSourceType()))
            .triggerMode(SyncTriggerMode.valueOf(dataObject.getTriggerMode()))
            .fileName(dataObject.getFileName())
            .fileHash(dataObject.getFileHash())
            .status(SyncRunStatus.valueOf(dataObject.getStatus()))
            .summaryJson(dataObject.getSummaryJson())
            .operator(dataObject.getOperator())
            .correlationBatchNo(dataObject.getCorrelationBatchNo())
            .startTime(dataObject.getStartTime())
            .endTime(dataObject.getEndTime())
            .build();
    }

    private SyncBatchDO toDataObject(SyncBatch batch) {
        SyncBatchDO dataObject = new SyncBatchDO();
        dataObject.setId(batch.getId());
        dataObject.setBatchNo(batch.getBatchNo());
        dataObject.setBatchType(batch.getBatchType().name());
        dataObject.setSourceType(batch.getSourceType().name());
        dataObject.setTriggerMode(batch.getTriggerMode().name());
        dataObject.setFileName(batch.getFileName());
        dataObject.setFileHash(batch.getFileHash());
        dataObject.setStatus(batch.getStatus().name());
        dataObject.setSummaryJson(batch.getSummaryJson());
        dataObject.setOperator(batch.getOperator());
        dataObject.setCorrelationBatchNo(batch.getCorrelationBatchNo());
        dataObject.setStartTime(batch.getStartTime());
        dataObject.setEndTime(batch.getEndTime());
        return dataObject;
    }
}
