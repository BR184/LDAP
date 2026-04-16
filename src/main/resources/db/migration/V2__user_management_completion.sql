INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
VALUES
('USER_UPDATE', '用户更新', 'API', '/api/v1/users/:id', 'PUT', 0, 9, 1, '用户管理'),
('USER_DELETE', '用户删除', 'API', '/api/v1/users/:id', 'DELETE', 0, 10, 1, '用户管理'),
('USER_PASSWORD_RESET', '用户密码重置', 'API', '/api/v1/users/:id/password/reset', 'PUT', 0, 11, 1, '用户管理');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT 1, id, 'system'
FROM sys_permission
WHERE permission_code IN ('USER_UPDATE', 'USER_DELETE', 'USER_PASSWORD_RESET');
