ALTER TABLE sys_import_batch
    ADD COLUMN cancelled_by VARCHAR(128) NULL COMMENT '取消人' AFTER expired_at,
    ADD COLUMN cancelled_at TIMESTAMP NULL COMMENT '取消时间' AFTER cancelled_by;

ALTER TABLE sys_import_change_item
    ADD COLUMN resolution_action VARCHAR(64) NULL COMMENT '冲突处理动作' AFTER executed_at,
    ADD COLUMN resolved_by VARCHAR(128) NULL COMMENT '冲突处理人' AFTER resolution_action,
    ADD COLUMN resolved_at TIMESTAMP NULL COMMENT '冲突处理时间' AFTER resolved_by;

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'IMPORT_PLAN_CONFLICT_RESOLVE', '导入阻断冲突处理', 'API', '/api/v1/import/plan/:id/conflicts/:itemId/skip', 'POST', 0, 67, 1, '文件导入'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'IMPORT_PLAN_CONFLICT_RESOLVE');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'IMPORT_PLAN_CANCEL', '导入草稿计划取消', 'API', '/api/v1/import/plan/:id/cancel', 'POST', 0, 68, 1, '文件导入'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'IMPORT_PLAN_CANCEL');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('IMPORT_PLAN_CONFLICT_RESOLVE', 'IMPORT_PLAN_CANCEL')
WHERE r.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
