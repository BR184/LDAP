INSERT INTO sys_menu_permission (menu_id, permission_id, creator)
SELECT m.id, p.id, 'system'
FROM sys_menu m
INNER JOIN sys_permission p ON p.permission_code = 'USER_READ'
LEFT JOIN sys_menu_permission mp ON mp.menu_id = m.id AND mp.permission_id = p.id
WHERE m.menu_code = 'USER_MANAGEMENT'
  AND mp.menu_id IS NULL;

DELETE rm
FROM sys_role_menu rm
INNER JOIN sys_role r ON r.id = rm.role_id
INNER JOIN sys_menu m ON m.id = rm.menu_id
WHERE r.role_code = 'DIRECT_MANAGER'
  AND m.menu_code IN ('UINIT0', 'PERSONNEL_MANAGEMENT', 'USER_MANAGEMENT');
