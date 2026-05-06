CREATE TABLE sys_mail_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    send_mode VARCHAR(32) NOT NULL,
    secure_mode VARCHAR(32) NOT NULL,
    host VARCHAR(128) NOT NULL,
    port INT NOT NULL,
    from_address VARCHAR(128) NOT NULL,
    from_name VARCHAR(128),
    auth_required TINYINT NOT NULL DEFAULT 1,
    username VARCHAR(128),
    password_ciphertext TEXT,
    enabled TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(256),
    last_test_success TINYINT,
    last_test_at DATETIME,
    last_test_message VARCHAR(512),
    creator VARCHAR(64),
    modifier VARCHAR(64),
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, path, component, icon, sort_no, status, visible, min_permission_level, remark, creator, modifier)
SELECT 'MAIL_CONFIG_MANAGEMENT', '邮件配置', id, 'MENU', '/system/mail-config', 'system/mail-config/index', 'message', 5, 1, 1, 2, '系统邮件配置菜单', 'system', 'system'
FROM sys_menu
WHERE menu_code = 'SYSTEM_MANAGEMENT'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'MAIL_CONFIG_MANAGEMENT');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'MAIL_CONFIG_READ', '邮件配置查询', 'API', '/api/v1/system/mail-config', 'GET', 0, 47, 1, '邮件配置'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'MAIL_CONFIG_READ');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'MAIL_CONFIG_SAVE', '邮件配置保存', 'API', '/api/v1/system/mail-config', 'PUT', 0, 48, 1, '邮件配置'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'MAIL_CONFIG_SAVE');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'MAIL_CONFIG_TEST', '邮件配置测试', 'API', '/api/v1/system/mail-config/test', 'POST', 0, 49, 1, '邮件配置'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'MAIL_CONFIG_TEST');

INSERT INTO sys_role_menu (role_id, menu_id, creator)
SELECT r.id, m.id, 'system'
FROM sys_role r
INNER JOIN sys_menu m ON m.menu_code = 'MAIL_CONFIG_MANAGEMENT'
LEFT JOIN sys_role_menu rm ON rm.role_id = r.id AND rm.menu_id = m.id
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND rm.role_id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
INNER JOIN sys_permission p ON p.permission_code IN ('MAIL_CONFIG_READ', 'MAIL_CONFIG_SAVE', 'MAIL_CONFIG_TEST')
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p.id
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND rp.role_id IS NULL;
