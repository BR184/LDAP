-- 以当前前端实际路由为权威来源，清理旧菜单元数据，避免授权项与左侧导航出现不同中文名称。
UPDATE sys_menu
SET menu_name = '部门管理',
    path = '/departments',
    remark = '部门管理菜单',
    modifier = 'system',
    gmt_modified = CURRENT_TIMESTAMP
WHERE menu_code = 'GROUP_MANAGEMENT';

UPDATE sys_permission
SET permission_name = '菜单显示_部门管理',
    resource_path = 'menu:GROUP_MANAGEMENT',
    remark = '部门管理页面显示权限',
    gmt_modified = CURRENT_TIMESTAMP
WHERE permission_code = 'MENU_VIEW_GROUP_MANAGEMENT';

UPDATE sys_menu
SET menu_name = 'LDAP 控制面',
    path = '/ldap',
    remark = 'LDAP 控制面菜单',
    modifier = 'system',
    gmt_modified = CURRENT_TIMESTAMP
WHERE menu_code = 'API_MANAGEMENT';

UPDATE sys_permission
SET permission_name = '菜单显示_LDAP 控制面',
    resource_path = 'menu:API_MANAGEMENT',
    remark = 'LDAP 控制面页面显示权限',
    gmt_modified = CURRENT_TIMESTAMP
WHERE permission_code = 'MENU_VIEW_API_MANAGEMENT';

-- FIELD_RELATION_MANAGEMENT 没有前端路由或页面实现，不保留不可访问的幽灵菜单和权限。
DELETE role_permission
FROM sys_role_permission role_permission
INNER JOIN sys_permission permission ON permission.id = role_permission.permission_id
WHERE permission.permission_code = 'MENU_VIEW_FIELD_RELATION_MANAGEMENT';

DELETE menu_permission
FROM sys_menu_permission menu_permission
INNER JOIN sys_menu menu ON menu.id = menu_permission.menu_id
WHERE menu.menu_code = 'FIELD_RELATION_MANAGEMENT';

DELETE FROM sys_permission
WHERE permission_code = 'MENU_VIEW_FIELD_RELATION_MANAGEMENT';

DELETE FROM sys_menu
WHERE menu_code = 'FIELD_RELATION_MANAGEMENT';
