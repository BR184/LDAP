package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.common.enums.SyncJobType;
import com.company.idm.common.enums.SyncRunStatus;
import com.company.idm.common.enums.SyncTargetType;
import com.company.idm.domain.sync.SyncJob;
import com.company.idm.domain.sync.SyncJobRepository;
import com.company.idm.infrastructure.persistence.dataobject.SyncJobDO;
import com.company.idm.infrastructure.persistence.mapper.SyncJobMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 实现同步任务仓储。
 */
@Repository
@RequiredArgsConstructor
public class MybatisSyncJobRepository implements SyncJobRepository {

    private final SyncJobMapper syncJobMapper;

    @Override
    public SyncJob save(SyncJob job) {
        SyncJobDO dataObject = toDataObject(job);
        if (dataObject.getId() == null) {
            dataObject.setGmtCreate(LocalDateTime.now());
            dataObject.setGmtModified(LocalDateTime.now());
            syncJobMapper.insert(dataObject);
        } else {
            dataObject.setGmtModified(LocalDateTime.now());
            syncJobMapper.updateById(dataObject);
        }
        return toDomain(dataObject);
    }

    @Override
    public Optional<SyncJob> findById(Long id) {
        return Optional.ofNullable(syncJobMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<SyncJob> findByBatchNo(String batchNo) {
        return syncJobMapper.selectList(new LambdaQueryWrapper<SyncJobDO>()
                .eq(SyncJobDO::getBatchNo, batchNo)
                .orderByAsc(SyncJobDO::getId))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<SyncJob> findRecent(int limit) {
        return syncJobMapper.selectList(new LambdaQueryWrapper<SyncJobDO>()
                .orderByDesc(SyncJobDO::getId)
                .last("LIMIT " + Math.max(1, limit)))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private SyncJob toDomain(SyncJobDO dataObject) {
        return SyncJob.builder()
            .id(dataObject.getId())
            .batchNo(dataObject.getBatchNo())
            .jobType(SyncJobType.valueOf(dataObject.getJobType()))
            .targetType(SyncTargetType.valueOf(dataObject.getTargetType()))
            .status(SyncRunStatus.valueOf(dataObject.getStatus()))
            .requestJson(dataObject.getRequestJson())
            .resultJson(dataObject.getResultJson())
            .errorMessage(dataObject.getErrorMessage())
            .operator(dataObject.getOperator())
            .retryCount(dataObject.getRetryCount())
            .startTime(dataObject.getStartTime())
            .endTime(dataObject.getEndTime())
            .build();
    }

    private SyncJobDO toDataObject(SyncJob job) {
        SyncJobDO dataObject = new SyncJobDO();
        dataObject.setId(job.getId());
        dataObject.setBatchNo(job.getBatchNo());
        dataObject.setJobType(job.getJobType().name());
        dataObject.setTargetType(job.getTargetType().name());
        dataObject.setStatus(job.getStatus().name());
        dataObject.setRequestJson(job.getRequestJson());
        dataObject.setResultJson(job.getResultJson());
        dataObject.setErrorMessage(job.getErrorMessage());
        dataObject.setOperator(job.getOperator());
        dataObject.setRetryCount(job.getRetryCount());
        dataObject.setStartTime(job.getStartTime());
        dataObject.setEndTime(job.getEndTime());
        return dataObject;
    }
}
