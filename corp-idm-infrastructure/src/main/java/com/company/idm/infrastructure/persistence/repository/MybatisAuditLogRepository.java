package com.company.idm.infrastructure.persistence.repository;

import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.infrastructure.persistence.dataobject.AuditLogDO;
import com.company.idm.infrastructure.persistence.mapper.AuditLogMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisAuditLogRepository implements AuditLogRepository {

    private final AuditLogMapper auditLogMapper;

    @Override
    public void save(AuditLog auditLog) {
        AuditLogDO dataObject = new AuditLogDO();
        dataObject.setTraceId(auditLog.getTraceId());
        dataObject.setOperator(auditLog.getOperator());
        dataObject.setOperationType(auditLog.getOperationType());
        dataObject.setBizType(auditLog.getBizType());
        dataObject.setBizId(auditLog.getBizId());
        dataObject.setBeforeJson(auditLog.getBeforeJson());
        dataObject.setAfterJson(auditLog.getAfterJson());
        dataObject.setResult(auditLog.getResult());
        dataObject.setErrorMsg(auditLog.getErrorMessage());
        dataObject.setGmtCreate(LocalDateTime.now());
        auditLogMapper.insert(dataObject);
    }
}

