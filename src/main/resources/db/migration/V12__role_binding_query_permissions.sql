INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'ROLE_MENU_READ_BINDINGS', '角色菜单绑定查询', 'API', '/api/v1/roles/:id/menus', 'GET', 0, 45, 1, '角色管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'ROLE_MENU_READ_BINDINGS');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'ROLE_PERMISSION_READ_BINDINGS', '角色权限绑定查询', 'API', '/api/v1/roles/:id/permissions', 'GET', 0, 46, 1, '角色管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'ROLE_PERMISSION_READ_BINDINGS');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON p.permission_code = 'ROLE_MENU_READ_BINDINGS'
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p.id
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND rp.role_id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON p.permission_code = 'ROLE_PERMISSION_READ_BINDINGS'
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p.id
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND rp.role_id IS NULL;
