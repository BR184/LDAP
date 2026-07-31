INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'USER_SUBORDINATE_TREE_READ', '直属下级及递归下级查询', 'API', '/api/v1/users', 'GET', 0, 70, 1, '用户管理数据范围：仅直属及递归下级'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE permission_code = 'USER_SUBORDINATE_TREE_READ'
);

INSERT INTO sys_menu_permission (menu_id, permission_id, creator)
SELECT m.id, p.id, 'system'
FROM sys_menu m
INNER JOIN sys_permission p ON p.permission_code = 'USER_SUBORDINATE_TREE_READ'
LEFT JOIN sys_menu_permission mp ON mp.menu_id = m.id AND mp.permission_id = p.id
WHERE m.menu_code = 'USER_MANAGEMENT'
  AND mp.menu_id IS NULL;

DELETE rp
FROM sys_role_permission rp
INNER JOIN sys_role r ON r.id = rp.role_id
INNER JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code = 'DIRECT_MANAGER'
  AND p.permission_code = 'USER_READ';

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON p.permission_code = 'USER_SUBORDINATE_TREE_READ'
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p.id
WHERE r.role_code = 'DIRECT_MANAGER'
  AND rp.role_id IS NULL;

UPDATE sys_role
SET remark = '拥有直属及递归下级查询和直属下属密码重置权限',
    modifier = 'system',
    gmt_modified = CURRENT_TIMESTAMP
WHERE role_code = 'DIRECT_MANAGER';
