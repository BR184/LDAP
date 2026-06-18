INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'IMPORT_PLAN_DETAIL', '飞书导入计划详情', 'API', '/api/v1/import/plan/:id', 'GET', 0, 65, 1, '文件导入'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'IMPORT_PLAN_DETAIL');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'IMPORT_PLAN_RETRY_LDAP', '飞书导入 LDAP 失败重试', 'API', '/api/v1/import/plan/:id/execute', 'POST', 0, 66, 1, '文件导入'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'IMPORT_PLAN_RETRY_LDAP');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'IMPORT_PLAN_DETAIL',
    'IMPORT_PLAN_RETRY_LDAP'
)
WHERE r.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
