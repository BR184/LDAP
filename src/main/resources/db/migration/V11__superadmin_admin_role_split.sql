UPDATE sys_role
SET role_code = 'SUPER_ADMIN',
    role_name = '超级管理员',
    permission_level = 1,
    built_in = 1,
    status = 1,
    remark = '系统内置超级管理员角色'
WHERE role_code = 'ADMIN';

INSERT INTO sys_role (role_code, role_name, permission_level, built_in, status, remark, creator, modifier)
SELECT 'ADMIN', '管理员', 2, 1, 1, '系统内置管理员角色', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'ADMIN');

UPDATE sys_user
SET real_name = '超级管理员'
WHERE username = 'admin' AND deleted = 0;

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON p.status = 1
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p.id
WHERE r.role_code = 'ADMIN'
  AND rp.role_id IS NULL;

INSERT INTO sys_role_menu (role_id, menu_id, creator)
SELECT r.id, m.id, 'system'
FROM sys_role r
INNER JOIN sys_menu m ON m.status = 1 AND m.visible = 1
LEFT JOIN sys_role_menu rm ON rm.role_id = r.id AND rm.menu_id = m.id
WHERE r.role_code = 'ADMIN'
  AND rm.role_id IS NULL;
