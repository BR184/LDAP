UPDATE sys_menu
SET path = '/users',
    gmt_modified = CURRENT_TIMESTAMP
WHERE menu_code = 'USER_MANAGEMENT';

INSERT INTO sys_role_menu (role_id, menu_id, creator)
SELECT r.id, m.id, 'system'
FROM sys_role r
INNER JOIN sys_menu m ON m.menu_code IN ('UINIT0', 'PERSONNEL_MANAGEMENT', 'USER_MANAGEMENT')
LEFT JOIN sys_role_menu rm ON rm.role_id = r.id AND rm.menu_id = m.id
WHERE r.role_code = 'DIRECT_MANAGER'
  AND rm.role_id IS NULL;
