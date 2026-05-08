UPDATE sys_user
SET username = CASE
    WHEN deleted = 1 THEN CONCAT(
        COALESCE(NULLIF(external_id, ''), NULLIF(username, ''), 'deleted_user'),
        '__deleted__',
        id
    )
    WHEN external_id IS NOT NULL AND external_id <> '' THEN external_id
    WHEN username = 'admin' THEN 'admin'
    WHEN employee_no IS NOT NULL AND employee_no <> '' THEN CONCAT('manual_', employee_no)
    ELSE CONCAT('manual_', id)
END;

ALTER TABLE sys_user
    CHANGE COLUMN username user_id VARCHAR(128) NOT NULL COMMENT '用户ID';

ALTER TABLE sys_user
    DROP COLUMN external_id;
