ALTER TABLE sys_import_change_item
    ADD COLUMN field_key VARCHAR(64) NULL COMMENT '稳定导入字段键（UPDATE时使用）' AFTER change_type;

UPDATE sys_import_change_item
SET field_key = CASE
    WHEN target_type = 'DEPARTMENT' AND field_name = 'deptName' THEN 'DEPARTMENT_NAME'
    WHEN target_type = 'DEPARTMENT' AND field_name = 'parentDeptCode' THEN 'DEPARTMENT_PARENT'
    WHEN target_type = 'DEPARTMENT' AND field_name = 'ancestorPath' THEN 'DEPARTMENT_PATH'
    WHEN target_type = 'DEPARTMENT' AND field_name = 'deptLevel' THEN 'DEPARTMENT_LEVEL'
    WHEN target_type = 'DEPARTMENT' AND field_name = 'status' THEN 'DEPARTMENT_STATUS'
    WHEN target_type = 'USER' AND field_name = 'realName' THEN 'USER_REAL_NAME'
    WHEN target_type = 'USER' AND field_name = 'email' THEN 'USER_EMAIL'
    WHEN target_type = 'USER' AND field_name = 'mobile' THEN 'USER_MOBILE'
    WHEN target_type = 'USER' AND field_name = 'employeeNo' THEN 'USER_EMPLOYEE_NO'
    WHEN target_type = 'USER' AND field_name = 'deptCode' THEN 'USER_MAIN_DEPARTMENT'
    WHEN target_type = 'USER' AND field_name = 'jobTitle' THEN 'USER_JOB_TITLE'
    WHEN target_type = 'USER' AND field_name = 'directLeaderRaw' THEN 'USER_DIRECT_LEADER'
    WHEN target_type = 'USER' AND field_name = 'leaderRef' THEN 'USER_LEADER_REFERENCE'
    WHEN target_type = 'USER' AND field_name = 'accountStatus' THEN 'USER_ACCOUNT_STATUS'
    WHEN target_type = 'USER' AND field_name = 'partTimeDeptCodes' THEN 'USER_PART_TIME_DEPARTMENTS'
    WHEN target_type = 'USER' AND field_name IN ('status', 'employmentStatus') THEN 'USER_EMPLOYMENT_STATUS'
    ELSE NULL
END;

UPDATE sys_import_change_item
SET before_json = CASE
        WHEN before_json IS NOT NULL AND JSON_VALID(before_json) THEN JSON_REMOVE(
            before_json,
            '$.intranetEmail',
            '$.status',
            '$.sourceType',
            '$.ldapDn',
            '$.tokenVersion'
        )
        ELSE before_json
    END,
    after_json = CASE
        WHEN after_json IS NOT NULL AND JSON_VALID(after_json) THEN JSON_REMOVE(
            after_json,
            '$.intranetEmail',
            '$.status',
            '$.sourceType',
            '$.ldapDn',
            '$.tokenVersion'
        )
        ELSE after_json
    END
WHERE target_type IN ('USER', 'LDAP_USER');

ALTER TABLE sys_import_change_item
    DROP COLUMN field_name;
