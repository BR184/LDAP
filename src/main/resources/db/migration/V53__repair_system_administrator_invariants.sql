UPDATE sys_role
SET permission_level = 1,
    built_in = 1,
    status = 1,
    role_scope = 'SYSTEM',
    role_group_id = NULL,
    modifier = 'system:v53',
    gmt_modified = CURRENT_TIMESTAMP
WHERE role_code = 'SUPER_ADMIN';

UPDATE sys_user
SET token_version = token_version + CASE
        WHEN access_allowed <> 1 OR employment_status <> 'ACTIVE' THEN 1
        ELSE 0
    END,
    access_allowed = 1,
    employment_status = 'ACTIVE',
    account_status = '正常',
    modifier = 'system:v53',
    gmt_modified = CURRENT_TIMESTAMP
WHERE user_id = 'admin'
  AND deleted = 0;

INSERT IGNORE INTO sys_user_role (user_id, role_id, creator)
SELECT user_account.id, role.id, 'system:v53'
FROM sys_user user_account
INNER JOIN sys_role role ON role.role_code = 'SUPER_ADMIN'
WHERE user_account.user_id = 'admin'
  AND user_account.deleted = 0;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, creator)
SELECT role.id, permission.id, 'system:v53'
FROM sys_role role
INNER JOIN sys_permission permission ON permission.status = 1
WHERE role.role_code = 'SUPER_ADMIN';
