INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
VALUES
('MENU_DETAIL', '菜单详情查询', 'API', '/api/v1/menus/:id', 'GET', 0, 19, 1, '菜单管理'),
('MENU_CREATE', '菜单创建', 'API', '/api/v1/menus', 'POST', 0, 20, 1, '菜单管理'),
('MENU_UPDATE', '菜单更新', 'API', '/api/v1/menus/:id', 'PUT', 0, 21, 1, '菜单管理'),
('MENU_DELETE', '菜单删除', 'API', '/api/v1/menus/:id', 'DELETE', 0, 22, 1, '菜单管理');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON 1 = 1
WHERE r.role_code = 'ADMIN'
  AND p.permission_code IN ('MENU_DETAIL', 'MENU_CREATE', 'MENU_UPDATE', 'MENU_DELETE');
