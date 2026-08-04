ALTER TABLE sys_role
    ADD COLUMN role_scope VARCHAR(16) NOT NULL DEFAULT 'SYSTEM' COMMENT '角色作用域：GLOBAL/GROUP/SYSTEM' AFTER remark,
    ADD COLUMN role_group_id BIGINT NULL COMMENT 'GROUP角色所属角色组' AFTER role_scope,
    ADD KEY idx_role_scope_group (role_scope, role_group_id, status);

UPDATE sys_role
SET role_scope = CASE WHEN role_code = 'NORMAL_USER' THEN 'GLOBAL' ELSE 'SYSTEM' END,
    role_group_id = NULL;

CREATE TABLE sys_role_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色组ID',
    group_name VARCHAR(128) NOT NULL COMMENT '角色组名称',
    remark VARCHAR(256) NULL COMMENT '备注',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    creator VARCHAR(64) NOT NULL,
    modifier VARCHAR(64) NOT NULL,
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_role_group_status_name (status, group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委派角色组';

CREATE TABLE sys_role_group_member (
    group_id BIGINT NOT NULL COMMENT '角色组ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    member_role VARCHAR(16) NOT NULL COMMENT 'OWNER/MANAGER',
    creator VARCHAR(64) NOT NULL,
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (group_id, user_id),
    KEY idx_role_group_member_user (user_id, group_id),
    KEY idx_role_group_member_owner (group_id, member_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色组协作成员';

CREATE TABLE sys_role_membership_change (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '单调递增同步游标',
    role_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    role_scope VARCHAR(16) NOT NULL,
    role_group_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    member_name VARCHAR(64) NOT NULL,
    change_type VARCHAR(16) NOT NULL COMMENT 'ADDED/REMOVED',
    operator VARCHAR(64) NOT NULL,
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_role_membership_cursor_scope (id, role_scope, role_group_id),
    KEY idx_role_membership_role_cursor (role_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放角色成员增量事件';

ALTER TABLE sys_personal_access_token
    MODIFY COLUMN user_id BIGINT NULL COMMENT 'USER主体所属用户ID',
    ADD COLUMN subject_type VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT 'USER/ROLE_GROUP/GLOBAL' AFTER user_id,
    ADD COLUMN subject_id BIGINT NULL COMMENT '主体ID；GLOBAL为空' AFTER subject_type,
    ADD KEY idx_pat_subject_created (subject_type, subject_id, gmt_create, id),
    ADD KEY idx_pat_subject_active (subject_type, subject_id, revoked_at, expires_at);

UPDATE sys_personal_access_token
SET subject_type = 'USER',
    subject_id = user_id
WHERE subject_type IS NULL OR subject_type = '' OR subject_type = 'USER';

INSERT INTO sys_permission (
    permission_code, permission_name, permission_type, resource_path, action,
    parent_id, sort_no, status, remark
)
VALUES
    ('ROLE_GROUP_MANAGE', '委派角色组管理', 'API', '/api/v1/role-groups', 'MANAGE', 0, 81, 1, '角色组定义、协作成员、组角色和组令牌管理'),
    ('ROLE_GROUP_USER_ASSIGN', '委派用户分配', 'API', '/api/v1/role-groups/:id/roles/:roleId/members', 'MANAGE', 0, 82, 1, '对本组角色和全局普通角色维护成员关系'),
    ('ROLE_SCOPE_MANAGE', '角色作用域管理', 'API', '/api/v1/role-governance/roles/:id/scope', 'PUT', 0, 83, 1, '平台管理员维护GLOBAL/GROUP/SYSTEM作用域'),
    ('MENU_VIEW_ROLE_GROUP_MANAGEMENT', '菜单显示_角色组管理', 'MENU', 'menu:ROLE_GROUP_MANAGEMENT', 'VIEW', 0, 110, 1, '角色组管理页面显示权限')
AS incoming_permission
ON DUPLICATE KEY UPDATE
    permission_name = incoming_permission.permission_name,
    permission_type = incoming_permission.permission_type,
    resource_path = incoming_permission.resource_path,
    action = incoming_permission.action,
    sort_no = incoming_permission.sort_no,
    status = incoming_permission.status,
    remark = incoming_permission.remark,
    gmt_modified = CURRENT_TIMESTAMP;

INSERT INTO sys_menu (
    menu_code, menu_name, parent_id, menu_type, path, component, icon, sort_no,
    status, visible, remark, creator, modifier
)
SELECT
    'ROLE_GROUP_MANAGEMENT', '角色组管理', parent.id, 'MENU', '/role-groups',
    'system/role-group/index', 'connection', 4,
    1, 1, '委派角色组与开放角色成员供给管理', 'system', 'system'
FROM sys_menu parent
WHERE parent.menu_code = 'SYSTEM_MANAGEMENT'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'ROLE_GROUP_MANAGEMENT');

INSERT INTO sys_menu_permission (menu_id, permission_id, creator)
SELECT menu.id, permission.id, 'system'
FROM sys_menu menu
INNER JOIN sys_permission permission
    ON permission.permission_code = 'MENU_VIEW_ROLE_GROUP_MANAGEMENT'
LEFT JOIN sys_menu_permission binding
    ON binding.menu_id = menu.id AND binding.permission_id = permission.id
WHERE menu.menu_code = 'ROLE_GROUP_MANAGEMENT'
  AND binding.menu_id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT role.id, permission.id, 'system'
FROM sys_role role
INNER JOIN sys_permission permission
    ON permission.permission_code IN (
        'ROLE_GROUP_MANAGE',
        'ROLE_GROUP_USER_ASSIGN',
        'ROLE_SCOPE_MANAGE',
        'MENU_VIEW_ROLE_GROUP_MANAGEMENT'
    )
LEFT JOIN sys_role_permission binding
    ON binding.role_id = role.id AND binding.permission_id = permission.id
WHERE role.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND binding.role_id IS NULL;
