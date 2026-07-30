ALTER TABLE sys_import_change_item
    ADD COLUMN conflict_code VARCHAR(96) NULL COMMENT '稳定冲突编码' AFTER block_reason;

UPDATE sys_import_change_item
SET conflict_code = CASE block_reason
    WHEN '同一个飞书部门 external_id 在导入文件中重复' THEN 'FILE_DUPLICATE_DEPARTMENT_EXTERNAL_ID'
    WHEN '同一个部门编码在导入文件中重复' THEN 'FILE_DUPLICATE_DEPARTMENT_CODE'
    WHEN '同一个飞书 user_id 在导入文件中重复' THEN 'FILE_DUPLICATE_USER_ID'
    WHEN '同一个工号在导入文件中重复' THEN 'FILE_DUPLICATE_EMPLOYEE_NO'
    WHEN '飞书部门 external_id 与 dept_code 映射冲突' THEN 'DEPARTMENT_EXTERNAL_ID_CODE_MISMATCH'
    WHEN '部门编码已被其他飞书部门占用' THEN 'DEPARTMENT_CODE_OWNED_BY_ANOTHER_DEPARTMENT'
    WHEN 'LDAP group DN 将与已有非本部门条目冲突' THEN 'LDAP_GROUP_DN_OCCUPIED'
    WHEN '飞书 user_id 匹配到用户 A，但工号匹配到用户 B' THEN 'USER_ID_EMPLOYEE_NO_MATCH_DIFFERENT_USERS'
    WHEN '飞书用户工号已属于其他平台用户' THEN 'EMPLOYEE_NO_OWNED_BY_ANOTHER_USER'
    WHEN 'LDAP uid 将与已有非本用户条目冲突' THEN 'LDAP_UID_OCCUPIED'
    ELSE 'OBJECT_VALIDATION_FAILED'
END
WHERE change_type = 'CONFLICT' AND conflict_code IS NULL;

UPDATE sys_import_change_item conflict_item
JOIN sys_import_change_item candidate_item
  ON candidate_item.batch_id = conflict_item.batch_id
 AND candidate_item.target_type = 'USER'
 AND candidate_item.target_key = conflict_item.target_key
 AND candidate_item.change_type = 'CREATE'
SET conflict_item.after_json = candidate_item.after_json
WHERE conflict_item.conflict_code = 'EMPLOYEE_NO_OWNED_BY_ANOTHER_USER'
  AND conflict_item.after_json IS NULL;

UPDATE sys_import_change_item conflict_item
JOIN sys_user existing_user
  ON existing_user.employee_no = JSON_UNQUOTE(JSON_EXTRACT(conflict_item.after_json, '$.employeeNo'))
 AND existing_user.deleted = 0
SET conflict_item.before_json = JSON_OBJECT(
    'id', existing_user.id,
    'userId', existing_user.user_id,
    'realName', existing_user.real_name,
    'email', existing_user.email,
    'intranetEmail', existing_user.intranet_email,
    'mobile', existing_user.mobile,
    'employeeNo', existing_user.employee_no,
    'deptCode', existing_user.dept_code,
    'jobTitle', existing_user.job_title,
    'directLeaderRaw', existing_user.direct_leader_raw,
    'leaderRef', existing_user.leader_ref,
    'accountStatus', existing_user.account_status,
    'partTimeDeptCodes', JSON_ARRAY(),
    'status', IF(existing_user.status = 1, 'ENABLED', 'DISABLED'),
    'employmentStatus', existing_user.employment_status,
    'sourceType', existing_user.source_type,
    'ldapDn', existing_user.ldap_dn,
    'tokenVersion', existing_user.token_version
)
WHERE conflict_item.conflict_code = 'EMPLOYEE_NO_OWNED_BY_ANOTHER_USER'
  AND conflict_item.before_json IS NULL;

INSERT INTO sys_permission (permission_code, permission_name, permission_type, resource_path, action, parent_id, sort_no, status, remark)
SELECT 'IMPORT_PLAN_CONFLICT_MERGE', '导入冲突按工号合并', 'API', '/api/v1/import/plan/:id/conflicts/:itemId/merge-by-employee-no', 'POST', 0, 69, 1, '文件导入'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'IMPORT_PLAN_CONFLICT_MERGE');

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT r.id, p.id, 'system'
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'IMPORT_PLAN_CONFLICT_MERGE'
WHERE r.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
