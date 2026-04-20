INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
VALUES
('USER_DETAIL', '用户详情查询', 'API', '/api/v1/users/:id', 'GET', 0, 37, 1, '用户管理'),
('USER_SYNC_LDAP', '用户手工同步LDAP', 'API', '/api/v1/users/:id/sync-ldap', 'POST', 0, 38, 1, '用户管理'),
('DEPT_SYNC_LDAP', '部门手工同步LDAP', 'API', '/api/v1/departments/:deptCode/sync-ldap', 'POST', 0, 39, 1, '部门管理');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON 1 = 1
WHERE r.role_code = 'ADMIN'
  AND p.permission_code IN ('USER_DETAIL', 'USER_SYNC_LDAP', 'DEPT_SYNC_LDAP');
