package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.common.enums.SyncDiffStatus;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncTargetType;
import com.company.idm.domain.sync.SyncDiff;
import com.company.idm.domain.sync.SyncDiffRepository;
import com.company.idm.infrastructure.persistence.dataobject.SyncDiffDO;
import com.company.idm.infrastructure.persistence.mapper.SyncDiffMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 实现同步差异仓储。
 */
@Repository
@RequiredArgsConstructor
public class MybatisSyncDiffRepository implements SyncDiffRepository {

    private final SyncDiffMapper syncDiffMapper;

    @Override
    public void saveAll(List<SyncDiff> diffs) {
        if (diffs == null || diffs.isEmpty()) {
            return;
        }
        for (SyncDiff diff : diffs) {
            SyncDiffDO dataObject = toDataObject(diff);
            dataObject.setGmtCreate(LocalDateTime.now());
            dataObject.setGmtModified(LocalDateTime.now());
            syncDiffMapper.insert(dataObject);
        }
    }

    @Override
    public List<SyncDiff> findByBatchNo(String batchNo) {
        return syncDiffMapper.selectList(new LambdaQueryWrapper<SyncDiffDO>()
                .eq(SyncDiffDO::getBatchNo, batchNo)
                .orderByAsc(SyncDiffDO::getId))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private SyncDiff toDomain(SyncDiffDO dataObject) {
        return SyncDiff.builder()
            .id(dataObject.getId())
            .batchNo(dataObject.getBatchNo())
            .jobId(dataObject.getJobId())
            .targetType(SyncTargetType.valueOf(dataObject.getTargetType()))
            .targetKey(dataObject.getTargetKey())
            .diffType(SyncDiffType.valueOf(dataObject.getDiffType()))
            .sourceSnapshot(dataObject.getSourceSnapshot())
            .targetSnapshot(dataObject.getTargetSnapshot())
            .repairable(dataObject.getRepairable())
            .status(SyncDiffStatus.valueOf(dataObject.getStatus()))
            .build();
    }

    private SyncDiffDO toDataObject(SyncDiff diff) {
        SyncDiffDO dataObject = new SyncDiffDO();
        dataObject.setId(diff.getId());
        dataObject.setBatchNo(diff.getBatchNo());
        dataObject.setJobId(diff.getJobId());
        dataObject.setTargetType(diff.getTargetType().name());
        dataObject.setTargetKey(diff.getTargetKey());
        dataObject.setDiffType(diff.getDiffType().name());
        dataObject.setSourceSnapshot(diff.getSourceSnapshot());
        dataObject.setTargetSnapshot(diff.getTargetSnapshot());
        dataObject.setRepairable(diff.getRepairable());
        dataObject.setStatus(diff.getStatus().name());
        return dataObject;
    }
}
