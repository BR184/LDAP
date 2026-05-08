ALTER TABLE sys_user
    MODIFY COLUMN username VARCHAR(128) NOT NULL COMMENT '用户ID';

UPDATE sys_user
SET username = CASE
    WHEN deleted = 1 THEN username
    WHEN external_id IS NOT NULL AND external_id <> '' THEN external_id
    WHEN username = 'admin' THEN 'admin'
    WHEN employee_no IS NOT NULL AND employee_no <> '' THEN CONCAT('manual_', employee_no)
    ELSE CONCAT('manual_', id)
END,
token_version = token_version + 1
WHERE deleted = 0;
