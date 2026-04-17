CREATE TABLE sys_sync_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_no VARCHAR(64) NOT NULL UNIQUE,
    batch_type VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    trigger_mode VARCHAR(32) NOT NULL,
    file_name VARCHAR(256),
    file_hash VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    summary_json TEXT,
    operator VARCHAR(64),
    correlation_batch_no VARCHAR(64),
    start_time TIMESTAMP NULL,
    end_time TIMESTAMP NULL,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_sync_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_no VARCHAR(64) NOT NULL,
    job_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_json TEXT,
    result_json TEXT,
    error_message VARCHAR(512),
    operator VARCHAR(64),
    retry_count INT NOT NULL DEFAULT 0,
    start_time TIMESTAMP NULL,
    end_time TIMESTAMP NULL,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_sync_diff (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_no VARCHAR(64) NOT NULL,
    job_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_key VARCHAR(128) NOT NULL,
    diff_type VARCHAR(64) NOT NULL,
    source_snapshot TEXT,
    target_snapshot TEXT,
    repairable TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sys_sync_job_batch_no ON sys_sync_job(batch_no);
CREATE INDEX idx_sys_sync_diff_batch_no ON sys_sync_diff(batch_no);
CREATE INDEX idx_sys_sync_diff_job_id ON sys_sync_diff(job_id);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
VALUES
('SYNC_FEISHU_PREVIEW', '飞书同步预览', 'API', '/api/v1/sync/feishu/preview', 'POST', 0, 28, 1, '同步管理'),
('SYNC_FEISHU_EXECUTE', '飞书同步执行', 'API', '/api/v1/sync/feishu/execute', 'POST', 0, 29, 1, '同步管理'),
('SYNC_RECONCILE_PREVIEW', 'LDAP 对账预览', 'API', '/api/v1/sync/reconcile/preview', 'POST', 0, 30, 1, '同步管理'),
('SYNC_RECONCILE_EXECUTE', 'LDAP 对账执行', 'API', '/api/v1/sync/reconcile/execute', 'POST', 0, 31, 1, '同步管理'),
('SYNC_JOB_RETRY', '同步任务重试', 'API', '/api/v1/sync/jobs/:id/retry', 'POST', 0, 32, 1, '同步管理'),
('SYNC_JOB_LIST', '同步任务查询', 'API', '/api/v1/sync/jobs', 'GET', 0, 33, 1, '同步管理'),
('SYNC_BATCH_DETAIL', '同步批次详情', 'API', '/api/v1/sync/batches/:batchNo', 'GET', 0, 34, 1, '同步管理');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON 1 = 1
WHERE r.role_code = 'ADMIN'
  AND p.permission_code IN (
    'SYNC_FEISHU_PREVIEW',
    'SYNC_FEISHU_EXECUTE',
    'SYNC_RECONCILE_PREVIEW',
    'SYNC_RECONCILE_EXECUTE',
    'SYNC_JOB_RETRY',
    'SYNC_JOB_LIST',
    'SYNC_BATCH_DETAIL'
  );
