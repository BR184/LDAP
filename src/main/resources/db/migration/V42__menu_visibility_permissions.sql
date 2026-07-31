-- 将导航入口从业务 API 权限中解耦：每个可访问页面只关联一个稳定的菜单显示权限。
INSERT INTO sys_permission (
    permission_code,
    permission_name,
    permission_type,
    resource_path,
    action,
    parent_id,
    sort_no,
    status,
    remark
)
VALUES
    ('MENU_VIEW_USER_MANAGEMENT', '菜单显示_用户管理', 'MENU', 'menu:USER_MANAGEMENT', 'VIEW', 0, 101, 1, '用户管理页面显示权限'),
    ('MENU_VIEW_GROUP_MANAGEMENT', '菜单显示_分组管理', 'MENU', 'menu:GROUP_MANAGEMENT', 'VIEW', 0, 102, 1, '分组管理页面显示权限'),
    ('MENU_VIEW_FIELD_RELATION_MANAGEMENT', '菜单显示_字段关系管理', 'MENU', 'menu:FIELD_RELATION_MANAGEMENT', 'VIEW', 0, 103, 1, '字段关系管理页面显示权限'),
    ('MENU_VIEW_API_MANAGEMENT', '菜单显示_接口管理', 'MENU', 'menu:API_MANAGEMENT', 'VIEW', 0, 104, 1, '接口管理页面显示权限'),
    ('MENU_VIEW_MENU_MANAGEMENT', '菜单显示_菜单管理', 'MENU', 'menu:MENU_MANAGEMENT', 'VIEW', 0, 105, 1, '菜单管理页面显示权限'),
    ('MENU_VIEW_ROLE_MANAGEMENT', '菜单显示_角色管理', 'MENU', 'menu:ROLE_MANAGEMENT', 'VIEW', 0, 106, 1, '角色管理页面显示权限'),
    ('MENU_VIEW_FILE_IMPORT_MANAGEMENT', '菜单显示_文件导入', 'MENU', 'menu:FILE_IMPORT_MANAGEMENT', 'VIEW', 0, 107, 1, '文件导入页面显示权限'),
    ('MENU_VIEW_MAIL_CONFIG_MANAGEMENT', '菜单显示_邮件配置', 'MENU', 'menu:MAIL_CONFIG_MANAGEMENT', 'VIEW', 0, 108, 1, '邮件配置页面显示权限'),
    ('MENU_VIEW_OPERATION_LOG', '菜单显示_同步任务', 'MENU', 'menu:OPERATION_LOG', 'VIEW', 0, 109, 1, '同步任务页面显示权限')
AS incoming_permission
ON DUPLICATE KEY UPDATE
    permission_name = incoming_permission.permission_name,
    permission_type = incoming_permission.permission_type,
    resource_path = incoming_permission.resource_path,
    action = incoming_permission.action,
    parent_id = incoming_permission.parent_id,
    sort_no = incoming_permission.sort_no,
    status = incoming_permission.status,
    remark = incoming_permission.remark,
    gmt_modified = CURRENT_TIMESTAMP;

-- 将原 sys_role_menu 中的页面授权迁移到同编码的菜单显示权限；目录节点由后端按祖先关系自动补齐。
INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT DISTINCT role_menu.role_id, permission.id, 'system'
FROM sys_role_menu role_menu
INNER JOIN sys_menu menu ON menu.id = role_menu.menu_id
INNER JOIN sys_permission permission
    ON permission.permission_code = CONCAT('MENU_VIEW_', menu.menu_code)
LEFT JOIN sys_role_permission role_permission
    ON role_permission.role_id = role_menu.role_id
    AND role_permission.permission_id = permission.id
WHERE menu.menu_type = 'MENU'
  AND permission.permission_type = 'MENU'
  AND role_permission.role_id IS NULL;

-- 旧版本中“用户查询”即已获得用户管理入口的角色，在切换后显式保留该入口。
INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT DISTINCT role_permission.role_id, menu_permission.id, 'system'
FROM sys_role_permission role_permission
INNER JOIN sys_permission data_permission ON data_permission.id = role_permission.permission_id
INNER JOIN sys_permission menu_permission
    ON menu_permission.permission_code = 'MENU_VIEW_USER_MANAGEMENT'
LEFT JOIN sys_role_permission existing_grant
    ON existing_grant.role_id = role_permission.role_id
    AND existing_grant.permission_id = menu_permission.id
WHERE data_permission.permission_code IN ('USER_READ', 'USER_READ_SELF_AND_SUBORDINATE_TREE')
  AND existing_grant.role_id IS NULL;

-- 清理旧 API 到菜单的关联。业务数据范围权限不再影响左侧导航。
DELETE menu_permission
FROM sys_menu_permission menu_permission
INNER JOIN sys_permission permission ON permission.id = menu_permission.permission_id
WHERE permission.permission_type = 'API';

-- 每个现有可访问页面都写入其唯一的菜单显示权限关联。
INSERT INTO sys_menu_permission (menu_id, permission_id, creator)
SELECT menu.id, permission.id, 'system'
FROM sys_menu menu
INNER JOIN sys_permission permission
    ON permission.permission_code = CONCAT('MENU_VIEW_', menu.menu_code)
WHERE menu.menu_type = 'MENU'
  AND menu.menu_code IN (
      'USER_MANAGEMENT',
      'GROUP_MANAGEMENT',
      'FIELD_RELATION_MANAGEMENT',
      'API_MANAGEMENT',
      'MENU_MANAGEMENT',
      'ROLE_MANAGEMENT',
      'FILE_IMPORT_MANAGEMENT',
      'MAIL_CONFIG_MANAGEMENT',
      'OPERATION_LOG'
  )
ON DUPLICATE KEY UPDATE
    creator = 'system';
