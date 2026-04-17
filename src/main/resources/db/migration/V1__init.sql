CREATE TABLE sys_department (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dept_code VARCHAR(64) NOT NULL UNIQUE,
    dept_name VARCHAR(128) NOT NULL,
    parent_dept_code VARCHAR(64),
    source_type VARCHAR(32) NOT NULL,
    external_id VARCHAR(128),
    status TINYINT NOT NULL DEFAULT 1,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    real_name VARCHAR(64) NOT NULL,
    email VARCHAR(128),
    mobile VARCHAR(32),
    employee_no VARCHAR(64),
    dept_code VARCHAR(64),
    status TINYINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    external_id VARCHAR(128),
    ldap_dn VARCHAR(256),
    token_version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    creator VARCHAR(64),
    modifier VARCHAR(64),
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(64) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(256),
    creator VARCHAR(64),
    modifier VARCHAR(64),
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_code VARCHAR(128) NOT NULL UNIQUE,
    permission_name VARCHAR(128) NOT NULL,
    permission_type VARCHAR(32) NOT NULL,
    resource_path VARCHAR(256) NOT NULL,
    action VARCHAR(32) NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    sort_no INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(256),
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    creator VARCHAR(64),
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    creator VARCHAR(64),
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE sys_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64),
    operator VARCHAR(64),
    operation_type VARCHAR(64),
    biz_type VARCHAR(64),
    biz_id VARCHAR(64),
    before_json TEXT,
    after_json TEXT,
    result VARCHAR(32),
    error_msg VARCHAR(512),
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO sys_department (dept_code, dept_name, parent_dept_code, source_type, status)
VALUES ('D001', '研发中心', NULL, 'MANUAL', 1);

INSERT INTO sys_role (role_code, role_name, status, remark, creator, modifier)
VALUES ('SUPER_ADMIN', '超级管理员', 1, '原型初始化管理员角色', 'system', 'system');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
VALUES
('AUTH_ME', '查看当前用户', 'API', '/api/v1/auth/me', 'GET', 0, 1, 1, '认证模块'),
('USER_READ', '用户查询', 'API', '/api/v1/users', 'GET', 0, 2, 1, '用户管理'),
('USER_CREATE', '用户创建', 'API', '/api/v1/users', 'POST', 0, 3, 1, '用户管理'),
('USER_STATUS', '用户状态变更', 'API', '/api/v1/users/:id/status', 'PUT', 0, 4, 1, '用户管理'),
('ROLE_READ', '角色查询', 'API', '/api/v1/roles', 'GET', 0, 5, 1, '角色管理'),
('ROLE_CREATE', '角色创建', 'API', '/api/v1/roles', 'POST', 0, 6, 1, '角色管理'),
('ROLE_PERMISSION_ASSIGN', '角色授权', 'API', '/api/v1/roles/:id/permissions', 'PUT', 0, 7, 1, '角色管理'),
('PERMISSION_TREE', '权限树查询', 'API', '/api/v1/permissions/tree', 'GET', 0, 8, 1, '权限管理');

INSERT INTO sys_user (username, real_name, email, mobile, employee_no, dept_code, status, source_type, ldap_dn, token_version, deleted, creator, modifier)
VALUES ('admin', '系统管理员', 'admin@corp.local', '13800000000', 'E0001', 'D001', 1, 'MANUAL',
        'uid=admin,ou=people,dc=corp,dc=local', 0, 0, 'system', 'system');

INSERT INTO sys_user_role (user_id, role_id, creator)
VALUES (1, 1, 'system');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT 1, id, 'system' FROM sys_permission;
