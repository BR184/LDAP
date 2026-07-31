-- 菜单显示权限依赖菜单路径与前端导航项精确一致，修正历史系统管理路径。
UPDATE sys_menu
SET path = '/menus',
    modifier = 'system',
    gmt_modified = CURRENT_TIMESTAMP
WHERE menu_code = 'MENU_MANAGEMENT';

UPDATE sys_menu
SET path = '/roles',
    modifier = 'system',
    gmt_modified = CURRENT_TIMESTAMP
WHERE menu_code = 'ROLE_MANAGEMENT';
