INSERT INTO sys_role (role_code, role_name, permission_level, built_in, status, remark, creator, modifier)
SELECT 'DIRECT_MANAGER', '直属上级', 3, 1, 1, '系统内置直属上级角色，仅用于下属查询与密码重置', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'DIRECT_MANAGER');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON p.permission_code IN ('AUTH_ME', 'USER_READ', 'ROLE_READ', 'DEPT_TREE', 'USER_DETAIL')
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p.id
WHERE r.role_code = 'DIRECT_MANAGER'
  AND rp.role_id IS NULL;
