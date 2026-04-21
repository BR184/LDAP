INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
VALUES
('LDAP_FRAMEWORK_READ', '第三方LDAP框架查询', 'API', '/api/v1/ldap/framework', 'GET', 0, 42, 1, '第三方LDAP接入'),
('LDAP_TEMPLATE_READ', '第三方LDAP模板查询', 'API', '/api/v1/ldap/templates/:systemCode', 'GET', 0, 43, 1, '第三方LDAP接入'),
('LDAP_PRECHECK_EXECUTE', '第三方LDAP预检执行', 'API', '/api/v1/ldap/precheck', 'POST', 0, 44, 1, '第三方LDAP接入');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON 1 = 1
WHERE r.role_code = 'ADMIN'
  AND p.permission_code IN (
    'LDAP_FRAMEWORK_READ',
    'LDAP_TEMPLATE_READ',
    'LDAP_PRECHECK_EXECUTE'
  );
