ALTER TABLE sys_user
    ADD COLUMN access_allowed TINYINT(1) NOT NULL DEFAULT 1 COMMENT '管理员控制的平台准入策略' AFTER employment_status;

UPDATE sys_user
SET access_allowed = CASE WHEN status = 1 THEN 1 ELSE 0 END;

CREATE TABLE sys_user_part_time_department (
    user_id BIGINT NOT NULL,
    dept_code VARCHAR(64) NOT NULL,
    sort_no INT NOT NULL,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, dept_code),
    KEY idx_user_part_time_department_dept (dept_code),
    KEY idx_user_part_time_department_order (user_id, sort_no)
);

INSERT INTO sys_user_part_time_department (user_id, dept_code, sort_no)
SELECT source.id, source.dept_code, MIN(source.sort_no)
FROM (
    SELECT
        u.id,
        TRIM(parts.dept_code) AS dept_code,
        parts.sort_no
    FROM sys_user u
    JOIN JSON_TABLE(
        CONCAT('["', REPLACE(u.part_time_dept_codes, ',', '","'), '"]'),
        '$[*]' COLUMNS (
            sort_no FOR ORDINALITY,
            dept_code VARCHAR(64) PATH '$'
        )
    ) parts
    WHERE u.part_time_dept_codes IS NOT NULL
      AND TRIM(u.part_time_dept_codes) <> ''
) source
WHERE source.dept_code <> ''
GROUP BY source.id, source.dept_code;

UPDATE sys_permission
SET permission_code = 'USER_ACCESS_UPDATE',
    permission_name = '用户准入策略变更',
    resource_path = '/api/v1/users/:id/access',
    remark = '仅允许管理员控制平台与 LDAP 登录准入'
WHERE permission_code = 'USER_STATUS';

ALTER TABLE sys_user
    DROP COLUMN status,
    DROP COLUMN part_time_dept_codes;
