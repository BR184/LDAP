INSERT INTO sys_permission (
    permission_code, permission_name, permission_type, resource_path, action,
    parent_id, sort_no, status, remark
)
VALUES (
    'ROLE_GROUP_READ', '委派角色组查询', 'API', '/api/v1/role-groups', 'GET',
    0, 84, 1, '查询当前账号可见的角色组、组角色与协作成员'
)
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

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT manage_binding.role_id, read_permission.id, 'system'
FROM sys_role_permission manage_binding
INNER JOIN sys_permission manage_permission
    ON manage_permission.id = manage_binding.permission_id
   AND manage_permission.permission_code = 'ROLE_GROUP_MANAGE'
INNER JOIN sys_permission read_permission
    ON read_permission.permission_code = 'ROLE_GROUP_READ'
LEFT JOIN sys_role_permission existing_binding
    ON existing_binding.role_id = manage_binding.role_id
   AND existing_binding.permission_id = read_permission.id
WHERE existing_binding.role_id IS NULL;
