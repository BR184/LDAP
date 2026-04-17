package com.company.idm.domain.sync;

import com.company.idm.common.enums.SyncDiffStatus;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 同步差异领域实体，描述导入预览或对账扫描出的差异项。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SyncDiff {

    private Long id;
    private String batchNo;
    private Long jobId;
    private SyncTargetType targetType;
    private String targetKey;
    private SyncDiffType diffType;
    private String sourceSnapshot;
    private String targetSnapshot;
    private Integer repairable;
    private SyncDiffStatus status;
}
