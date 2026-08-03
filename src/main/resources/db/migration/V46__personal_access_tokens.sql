CREATE TABLE sys_personal_access_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    token_uid VARCHAR(32) NOT NULL COMMENT '公开令牌定位ID',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    name VARCHAR(64) NOT NULL COMMENT '令牌用途名称',
    secret_hash VARCHAR(64) NOT NULL COMMENT '令牌SHA-256摘要',
    hash_version SMALLINT NOT NULL DEFAULT 1 COMMENT '摘要格式版本',
    token_prefix VARCHAR(64) NOT NULL COMMENT '非敏感展示前缀',
    expires_at DATETIME(3) NULL COMMENT '过期时间，为空表示永不过期',
    revoked_at DATETIME(3) NULL COMMENT '撤销时间',
    last_used_at DATETIME(3) NULL COMMENT '最近使用时间',
    last_used_ip VARCHAR(45) NULL COMMENT '最近使用来源IP',
    creator VARCHAR(64) NOT NULL COMMENT '创建人',
    modifier VARCHAR(64) NOT NULL COMMENT '最后修改人',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_pat_token_uid (token_uid),
    KEY idx_pat_user_created (user_id, gmt_create, id),
    KEY idx_pat_user_active (user_id, revoked_at, expires_at),
    KEY idx_pat_last_used (last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人访问令牌';

CREATE TABLE sys_personal_access_token_permission (
    token_id BIGINT NOT NULL COMMENT '个人访问令牌ID',
    permission_id BIGINT NOT NULL COMMENT '授权API权限ID',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (token_id, permission_id),
    KEY idx_pat_permission_permission (permission_id, token_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人访问令牌权限范围';

ALTER TABLE sys_audit_log
    ADD COLUMN credential_type VARCHAR(32) NULL COMMENT '认证凭证类型' AFTER operator_ip,
    ADD COLUMN credential_id BIGINT NULL COMMENT '认证凭证ID' AFTER credential_type,
    ADD KEY idx_audit_credential (credential_type, credential_id);
