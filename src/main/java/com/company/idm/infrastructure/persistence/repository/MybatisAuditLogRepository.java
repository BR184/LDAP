package com.company.idm.infrastructure.persistence.repository;

import com.company.idm.common.log.TraceIdConstants;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.infrastructure.persistence.dataobject.AuditLogDO;
import com.company.idm.infrastructure.persistence.mapper.AuditLogMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 实现审计日志仓储。
 */
@Repository
@RequiredArgsConstructor
public class MybatisAuditLogRepository implements AuditLogRepository {

    private final AuditLogMapper auditLogMapper;

    @Override
    public void save(AuditLog auditLog) {
        AuditLogDO dataObject = new AuditLogDO();
        dataObject.setTraceId(resolveTraceId(auditLog));
        dataObject.setOperator(auditLog.getOperator());
        dataObject.setOperatorIp(auditLog.getOperatorIp());
        AuthenticatedUserCredential credential = resolveCredential(auditLog);
        dataObject.setCredentialType(credential.type());
        dataObject.setCredentialId(credential.id());
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

    private String resolveTraceId(AuditLog auditLog) {
        if (auditLog.getTraceId() != null && !auditLog.getTraceId().isBlank()) {
            return auditLog.getTraceId();
        }
        return MDC.get(TraceIdConstants.MDC_KEY);
    }

    private AuthenticatedUserCredential resolveCredential(AuditLog auditLog) {
        if (auditLog.getCredentialType() != null && !auditLog.getCredentialType().isBlank()) {
            return new AuthenticatedUserCredential(auditLog.getCredentialType(), auditLog.getCredentialId());
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
            && authentication.getPrincipal() instanceof com.company.idm.infrastructure.security.AuthenticatedUser principal) {
            return new AuthenticatedUserCredential(principal.credentialType().name(), principal.credentialId());
        }
        return new AuthenticatedUserCredential(null, null);
    }

    private record AuthenticatedUserCredential(String type, Long id) {
    }
}

