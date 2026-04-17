ALTER TABLE sys_department ADD ancestor_path VARCHAR(512);
ALTER TABLE sys_department ADD dept_level INT NOT NULL DEFAULT 1;
ALTER TABLE sys_department ADD ldap_dn VARCHAR(512);

UPDATE sys_department
SET ancestor_path = CONCAT('/', dept_code),
    dept_level = 1
WHERE ancestor_path IS NULL;

CREATE UNIQUE INDEX uk_sys_department_external_id ON sys_department(external_id);
CREATE UNIQUE INDEX uk_sys_department_ldap_dn ON sys_department(ldap_dn);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
VALUES
('DEPT_TREE', '部门树查询', 'API', '/api/v1/departments/tree', 'GET', 0, 23, 1, '部门管理'),
('DEPT_DETAIL', '部门详情查询', 'API', '/api/v1/departments/:deptCode', 'GET', 0, 24, 1, '部门管理'),
('DEPT_CREATE', '部门创建', 'API', '/api/v1/departments', 'POST', 0, 25, 1, '部门管理'),
('DEPT_UPDATE', '部门更新', 'API', '/api/v1/departments/:deptCode', 'PUT', 0, 26, 1, '部门管理'),
('DEPT_DELETE', '部门删除', 'API', '/api/v1/departments/:deptCode', 'DELETE', 0, 27, 1, '部门管理');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON 1 = 1
WHERE r.role_code = 'ADMIN'
  AND p.permission_code IN ('DEPT_TREE', 'DEPT_DETAIL', 'DEPT_CREATE', 'DEPT_UPDATE', 'DEPT_DELETE');
