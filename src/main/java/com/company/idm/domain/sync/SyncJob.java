package com.company.idm.domain.sync;

import com.company.idm.common.enums.SyncJobType;
import com.company.idm.common.enums.SyncRunStatus;
import com.company.idm.common.enums.SyncTargetType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 同步任务领域实体，描述批次中的单个执行步骤。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SyncJob {

    private Long id;
    private String batchNo;
    private SyncJobType jobType;
    private SyncTargetType targetType;
    private SyncRunStatus status;
    private String requestJson;
    private String resultJson;
    private String errorMessage;
    private String operator;
    private Integer retryCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
