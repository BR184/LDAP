package com.company.idm.domain.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

