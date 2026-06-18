DELETE rp
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.permission_code IN (
    'FEISHU_FULL_FILE_IMPORT',
    'DEPT_FEISHU_FILE_IMPORT',
    'USER_FEISHU_FILE_IMPORT'
);

DELETE FROM sys_permission
WHERE permission_code IN (
    'FEISHU_FULL_FILE_IMPORT',
    'DEPT_FEISHU_FILE_IMPORT',
    'USER_FEISHU_FILE_IMPORT'
);
