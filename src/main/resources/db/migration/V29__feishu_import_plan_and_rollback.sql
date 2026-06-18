CREATE TABLE sys_import_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    batch_code VARCHAR(64) NOT NULL UNIQUE COMMENT '批次编号',
    file_name VARCHAR(255) NOT NULL COMMENT '导入文件名',
    file_hash VARCHAR(64) COMMENT '文件内容 SHA256',
    source_type VARCHAR(32) NOT NULL COMMENT '来源类型：FEISHU_EXPORT / FEISHU_API / MANUAL_FILE',
    total_items INT NOT NULL DEFAULT 0 COMMENT '变更项总数',
    enabled_items INT NOT NULL DEFAULT 0 COMMENT '启用项数量',
    conflict_items INT NOT NULL DEFAULT 0 COMMENT '冲突项数量',
    status VARCHAR(32) NOT NULL COMMENT '批次状态：DRAFT / CONFIRMED / EXECUTING / COMPLETED / FAILED / ROLLED_BACK',
    created_by VARCHAR(128) NOT NULL COMMENT '创建人',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    confirmed_by VARCHAR(128) COMMENT '确认人',
    confirmed_at TIMESTAMP NULL COMMENT '确认时间',
    executed_by VARCHAR(128) COMMENT '执行人',
    executed_at TIMESTAMP NULL COMMENT '执行时间',
    rollback_by VARCHAR(128) COMMENT '撤回人',
    rollback_at TIMESTAMP NULL COMMENT '撤回时间',
    expired_at TIMESTAMP NULL COMMENT '计划过期时间',
    remark TEXT COMMENT '备注',
    INDEX idx_import_batch_code (batch_code),
    INDEX idx_import_batch_status (status),
    INDEX idx_import_batch_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='飞书导入批次表';

CREATE TABLE sys_import_change_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    batch_id BIGINT NOT NULL COMMENT '导入批次ID',
    target_type VARCHAR(32) NOT NULL COMMENT '目标类型：USER / DEPARTMENT / LDAP_USER / LDAP_GROUP',
    target_key VARCHAR(255) NOT NULL COMMENT '目标业务键：user_id / dept_code',
    change_type VARCHAR(32) NOT NULL COMMENT '变更类型：CREATE / UPDATE / DISABLE / RESIGN / CONFLICT',
    field_name VARCHAR(128) COMMENT '字段名（UPDATE时使用）',
    before_value TEXT COMMENT '原值',
    after_value TEXT COMMENT '新值',
    before_json TEXT COMMENT '对象执行前完整快照（JSON）',
    after_json TEXT COMMENT '对象计划后完整快照（JSON）',
    object_version INT COMMENT '对象版本号（乐观锁）',
    default_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '系统默认是否执行',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '用户最终是否执行',
    requires_confirmation BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否必须人工确认',
    confirmed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已人工确认',
    risk_level VARCHAR(32) NOT NULL DEFAULT 'LOW' COMMENT '风险级别：LOW / MEDIUM / HIGH / BLOCKER',
    block_reason VARCHAR(512) COMMENT '阻断原因',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '执行状态：PENDING / EXECUTED / FAILED / SKIPPED / LDAP_FAILED / ROLLED_BACK',
    error_message TEXT COMMENT '失败原因',
    retry_count INT NOT NULL DEFAULT 0 COMMENT 'LDAP 失败重试次数',
    executed_at TIMESTAMP NULL COMMENT '执行时间',
    INDEX idx_import_change_batch_id (batch_id),
    INDEX idx_import_change_target (target_type, target_key),
    INDEX idx_import_change_status (status),
    INDEX idx_import_change_type (change_type),
    CONSTRAINT fk_import_change_batch FOREIGN KEY (batch_id) REFERENCES sys_import_batch(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入变更项表';

CREATE TABLE sys_import_rollback_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    batch_id BIGINT NOT NULL COMMENT '原导入批次ID',
    change_item_id BIGINT NOT NULL COMMENT '原变更项ID',
    target_type VARCHAR(32) NOT NULL COMMENT '目标类型',
    target_key VARCHAR(255) NOT NULL COMMENT '目标业务键',
    rollback_action VARCHAR(32) NOT NULL COMMENT '撤回动作：RESTORE_FIELDS / DISABLE_CREATED / RESTORE_STATUS / RESTORE_LDAP',
    restore_json TEXT COMMENT '恢复用快照（JSON）',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / EXECUTED / FAILED / CONFLICT',
    error_message TEXT COMMENT '失败原因',
    executed_at TIMESTAMP NULL COMMENT '执行时间',
    INDEX idx_import_rollback_batch_id (batch_id),
    INDEX idx_import_rollback_change_item_id (change_item_id),
    INDEX idx_import_rollback_status (status),
    CONSTRAINT fk_import_rollback_batch FOREIGN KEY (batch_id) REFERENCES sys_import_batch(id),
    CONSTRAINT fk_import_rollback_change FOREIGN KEY (change_item_id) REFERENCES sys_import_change_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入撤回项表';

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'IMPORT_PLAN_CREATE', '飞书导入计划生成', 'API', '/api/v1/import/plan', 'POST', 0, 60, 1, '文件导入'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'IMPORT_PLAN_CREATE');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'IMPORT_PLAN_READ', '飞书导入计划查询', 'API', '/api/v1/import/plan', 'GET', 0, 61, 1, '文件导入'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'IMPORT_PLAN_READ');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'IMPORT_PLAN_CONFIRM', '飞书导入计划确认', 'API', '/api/v1/import/plan/:id/confirm', 'POST', 0, 62, 1, '文件导入'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'IMPORT_PLAN_CONFIRM');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'IMPORT_PLAN_EXECUTE', '飞书导入计划执行', 'API', '/api/v1/import/plan/:id/execute', 'POST', 0, 63, 1, '文件导入'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'IMPORT_PLAN_EXECUTE');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'IMPORT_PLAN_ROLLBACK', '飞书导入计划撤回', 'API', '/api/v1/import/plan/:id/rollback', 'POST', 0, 64, 1, '文件导入'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'IMPORT_PLAN_ROLLBACK');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'IMPORT_PLAN_CREATE',
    'IMPORT_PLAN_READ',
    'IMPORT_PLAN_CONFIRM',
    'IMPORT_PLAN_EXECUTE',
    'IMPORT_PLAN_ROLLBACK'
)
WHERE r.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
