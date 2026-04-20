INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
VALUES
('DEPT_FEISHU_FILE_IMPORT', '部门飞书文件导入', 'API', '/api/v1/departments/import/feishu-file', 'POST', 0, 40, 1, '部门管理'),
('USER_FEISHU_FILE_IMPORT', '用户飞书文件导入', 'API', '/api/v1/users/import/feishu-file', 'POST', 0, 41, 1, '用户管理');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON 1 = 1
WHERE r.role_code = 'ADMIN'
  AND p.permission_code IN ('DEPT_FEISHU_FILE_IMPORT', 'USER_FEISHU_FILE_IMPORT');
