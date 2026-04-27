INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, path, component, icon, sort_no, status, visible, min_permission_level, remark, creator, modifier)
SELECT 'FILE_IMPORT_MANAGEMENT', '文件导入', id, 'MENU', '/system/imports', 'system/import/index', 'upload', 4, 1, 1, 2, '统一文件导入菜单', 'system', 'system'
FROM sys_menu
WHERE menu_code = 'SYSTEM_MANAGEMENT'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'FILE_IMPORT_MANAGEMENT');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'FEISHU_FULL_FILE_IMPORT', '飞书一键文件导入', 'API', '/api/v1/system/imports/feishu/full', 'POST', 0, 46, 1, '文件导入'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'FEISHU_FULL_FILE_IMPORT');

INSERT INTO sys_role_menu (role_id, menu_id, creator)
SELECT r.id, m.id, 'system'
FROM sys_role r
INNER JOIN sys_menu m ON m.menu_code = 'FILE_IMPORT_MANAGEMENT'
LEFT JOIN sys_role_menu rm ON rm.role_id = r.id AND rm.menu_id = m.id
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND rm.role_id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON p.permission_code = 'FEISHU_FULL_FILE_IMPORT'
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p.id
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND rp.role_id IS NULL;
