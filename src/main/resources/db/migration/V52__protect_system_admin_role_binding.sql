UPDATE sys_role
SET built_in = 1,
    status = 1,
    modifier = 'system:v52',
    gmt_modified = CURRENT_TIMESTAMP
WHERE role_code = 'SUPER_ADMIN';

INSERT IGNORE INTO sys_user_role (user_id, role_id, creator)
SELECT user_account.id, role.id, 'system:v52'
FROM sys_user user_account
INNER JOIN sys_role role ON role.role_code = 'SUPER_ADMIN'
WHERE user_account.user_id = 'admin'
  AND user_account.deleted = 0;
