package com.company.idm.domain.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 审计日志领域实体，描述关键操作的审计快照。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    private Long id;
    private String traceId;
    private String operator;
    private String operationType;
    private String bizType;
    private String bizId;
    private String beforeJson;
    private String afterJson;
    private String result;
    private String errorMessage;
}

