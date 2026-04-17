ALTER TABLE sys_role ADD permission_level INT NOT NULL DEFAULT 3;
ALTER TABLE sys_role ADD built_in TINYINT NOT NULL DEFAULT 0;

UPDATE sys_role
SET role_code = 'ADMIN',
    role_name = '管理员',
    permission_level = 1,
    built_in = 1
WHERE role_code = 'SUPER_ADMIN';

INSERT INTO sys_role (role_code, role_name, permission_level, built_in, status, remark, creator, modifier)
SELECT 'NORMAL_USER', '普通用户', 3, 1, 1, '系统初始化普通用户角色', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'NORMAL_USER');

CREATE TABLE sys_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    menu_code VARCHAR(64) NOT NULL UNIQUE,
    menu_name VARCHAR(128) NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    menu_type VARCHAR(32) NOT NULL,
    path VARCHAR(256),
    component VARCHAR(256),
    icon VARCHAR(64),
    sort_no INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    visible TINYINT NOT NULL DEFAULT 1,
    min_permission_level INT NOT NULL DEFAULT 3,
    remark VARCHAR(256),
    creator VARCHAR(64),
    modifier VARCHAR(64),
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    creator VARCHAR(64),
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, menu_id)
);

INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, path, component, icon, sort_no, status, visible, min_permission_level, remark, creator, modifier)
VALUES
('UINIT0', 'uinit0', 0, 'CATALOG', '/uinit0', 'Layout', 'app', 1, 1, 1, 1, '根菜单', 'system', 'system'),
('PERSONNEL_MANAGEMENT', '人员管理', 1, 'CATALOG', '/personnel', 'Layout', 'team', 1, 1, 1, 1, '人员管理目录', 'system', 'system'),
('USER_MANAGEMENT', '用户管理', 2, 'MENU', '/personnel/users', 'personnel/user/index', 'user', 1, 1, 1, 1, '用户管理菜单', 'system', 'system'),
('GROUP_MANAGEMENT', '分组管理', 2, 'MENU', '/personnel/groups', 'personnel/group/index', 'group', 2, 1, 1, 1, '分组管理菜单', 'system', 'system'),
('FIELD_RELATION_MANAGEMENT', '字段关系管理', 2, 'MENU', '/personnel/fields', 'personnel/field/index', 'field', 3, 1, 1, 1, '字段关系管理菜单', 'system', 'system'),
('SYSTEM_MANAGEMENT', '系统管理', 1, 'CATALOG', '/system', 'Layout', 'setting', 2, 1, 1, 1, '系统管理目录', 'system', 'system'),
('API_MANAGEMENT', '接口管理', 6, 'MENU', '/system/apis', 'system/api/index', 'api', 1, 1, 1, 1, '接口管理菜单', 'system', 'system'),
('MENU_MANAGEMENT', '菜单管理', 6, 'MENU', '/system/menus', 'system/menu/index', 'menu', 2, 1, 1, 1, '菜单管理菜单', 'system', 'system'),
('ROLE_MANAGEMENT', '角色管理', 6, 'MENU', '/system/roles', 'system/role/index', 'role', 3, 1, 1, 1, '角色管理菜单', 'system', 'system'),
('LOG_MANAGEMENT', '日志管理', 1, 'CATALOG', '/logs', 'Layout', 'log', 3, 1, 1, 1, '日志管理目录', 'system', 'system'),
('OPERATION_LOG', '操作日志', 10, 'MENU', '/logs/operations', 'logs/operation/index', 'audit', 1, 1, 1, 1, '操作日志菜单', 'system', 'system');

INSERT INTO sys_role_menu (role_id, menu_id, creator)
SELECT r.id, m.id, 'system'
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'ADMIN';

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
VALUES
('ROLE_DETAIL', '角色详情查询', 'API', '/api/v1/roles/:id', 'GET', 0, 12, 1, '角色管理'),
('ROLE_UPDATE', '角色更新', 'API', '/api/v1/roles/:id', 'PUT', 0, 13, 1, '角色管理'),
('ROLE_DELETE', '角色删除', 'API', '/api/v1/roles/:id', 'DELETE', 0, 14, 1, '角色管理'),
('ROLE_STATUS', '角色状态更新', 'API', '/api/v1/roles/:id/status', 'PUT', 0, 15, 1, '角色管理'),
('ROLE_MENU_BIND', '角色菜单绑定', 'API', '/api/v1/roles/:id/menus', 'PUT', 0, 16, 1, '角色管理'),
('USER_ROLE_ASSIGN', '用户角色分配', 'API', '/api/v1/users/:id/roles', 'PUT', 0, 17, 1, '角色管理'),
('MENU_TREE', '菜单树查询', 'API', '/api/v1/menus/tree', 'GET', 0, 18, 1, '菜单管理');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON 1 = 1
WHERE r.role_code = 'ADMIN'
  AND p.permission_code IN ('ROLE_DETAIL', 'ROLE_UPDATE', 'ROLE_DELETE', 'ROLE_STATUS', 'ROLE_MENU_BIND', 'USER_ROLE_ASSIGN', 'MENU_TREE');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON p.permission_code = 'AUTH_ME'
WHERE r.role_code = 'NORMAL_USER';
