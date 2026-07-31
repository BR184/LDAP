-- 旧角色菜单关系已在 V42 迁移至 sys_role_permission，不再保留双轨授权模型。
DELETE role_permission
FROM sys_role_permission role_permission
INNER JOIN sys_permission permission ON permission.id = role_permission.permission_id
WHERE permission.permission_code IN ('ROLE_MENU_BIND', 'ROLE_MENU_READ_BINDINGS');

DELETE FROM sys_permission
WHERE permission_code IN ('ROLE_MENU_BIND', 'ROLE_MENU_READ_BINDINGS');

DROP TABLE sys_role_menu;

ALTER TABLE sys_menu
    DROP COLUMN min_permission_level;

ALTER TABLE sys_menu_permission
    ADD CONSTRAINT uk_sys_menu_permission_menu_id UNIQUE (menu_id);
