INSERT INTO sys_role (role_code, role_name, permission_level, built_in, status, remark, creator, modifier)
SELECT 'DIRECT_MANAGER', '直属上级', 10, 1, 1, '拥有直属下属密码重置权限', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'DIRECT_MANAGER');

UPDATE sys_role
SET role_name = '直属上级',
    permission_level = 10,
    built_in = 1,
    status = 1,
    remark = '拥有直属下属密码重置权限',
    modifier = 'system',
    gmt_modified = CURRENT_TIMESTAMP
WHERE role_code = 'DIRECT_MANAGER';

INSERT INTO sys_role (role_code, role_name, permission_level, built_in, status, remark, creator, modifier)
SELECT 'TREE_MANAGER', '部门经理', 20, 1, 1, '拥有递归下级和直属下属密码重置权限', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'TREE_MANAGER');

UPDATE sys_role
SET role_name = '部门经理',
    permission_level = 20,
    built_in = 1,
    status = 1,
    remark = '拥有递归下级和直属下属密码重置权限',
    modifier = 'system',
    gmt_modified = CURRENT_TIMESTAMP
WHERE role_code = 'TREE_MANAGER';

DELETE rp
FROM sys_role_permission rp
INNER JOIN sys_role r ON r.id = rp.role_id
INNER JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code IN ('DIRECT_MANAGER', 'TREE_MANAGER')
  AND p.permission_code IN ('USER_PASSWORD_RESET_DIRECT', 'USER_PASSWORD_RESET_TREE');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON p.permission_code = 'USER_PASSWORD_RESET_DIRECT'
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p.id
WHERE r.role_code = 'DIRECT_MANAGER'
  AND rp.role_id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON p.permission_code IN ('USER_PASSWORD_RESET_DIRECT', 'USER_PASSWORD_RESET_TREE')
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p.id
WHERE r.role_code = 'TREE_MANAGER'
  AND rp.role_id IS NULL;

UPDATE sys_department
SET dept_name = '系统后台',
    gmt_modified = CURRENT_TIMESTAMP
WHERE dept_code = 'D001'
  AND dept_name = '????';
