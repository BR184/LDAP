package com.company.idm.domain.audit;

/**
 * 定义审计日志的领域仓储接口。
 */
public interface AuditLogRepository {

    void save(AuditLog auditLog);
}

