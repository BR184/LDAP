INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'USER_DETAIL', '用户详情查询', 'API', '/api/v1/users/:id', 'GET', 0, 9, 1, '用户管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'USER_DETAIL');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'DEPT_TREE', '部门树查询', 'API', '/api/v1/departments/tree', 'GET', 0, 10, 1, '部门管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'DEPT_TREE');
