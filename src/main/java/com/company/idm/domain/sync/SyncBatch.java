package com.company.idm.domain.sync;

import com.company.idm.common.enums.SyncBatchType;
import com.company.idm.common.enums.SyncRunStatus;
import com.company.idm.common.enums.SyncSourceType;
import com.company.idm.common.enums.SyncTriggerMode;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 同步批次领域实体，描述一次导入或对账执行批次。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SyncBatch {

    private Long id;
    private String batchNo;
    private SyncBatchType batchType;
    private SyncSourceType sourceType;
    private SyncTriggerMode triggerMode;
    private String fileName;
    private String fileHash;
    private SyncRunStatus status;
    private String summaryJson;
    private String operator;
    private String correlationBatchNo;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
