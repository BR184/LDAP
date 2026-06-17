ALTER TABLE sys_audit_log
    ADD COLUMN operator_ip VARCHAR(45) NULL COMMENT '操作人IP' AFTER operator;

UPDATE sys_permission
SET permission_code = 'USER_PASSWORD_RESET_ALL',
    permission_name = '全局密码重置',
    resource_path = '/api/v1/users/:id/password/reset',
    action = 'PUT',
    remark = '用户管理'
WHERE permission_code = 'USER_PASSWORD_RESET';

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'USER_PASSWORD_RESET_DIRECT', '直属下属密码重置', 'API', '/api/v1/users/:id/password/reset', 'PUT', 0, 12, 1, '用户管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'USER_PASSWORD_RESET_DIRECT');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'USER_PASSWORD_RESET_TREE', '递归下级密码重置', 'API', '/api/v1/users/:id/password/reset', 'PUT', 0, 13, 1, '用户管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'USER_PASSWORD_RESET_TREE');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON p.permission_code IN (
    'USER_PASSWORD_RESET_ALL',
    'USER_PASSWORD_RESET_DIRECT',
    'USER_PASSWORD_RESET_TREE'
)
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p.id
WHERE r.role_code = 'SUPER_ADMIN'
  AND rp.role_id IS NULL;
