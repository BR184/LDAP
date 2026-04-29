UPDATE sys_user
SET employee_no = NULL
WHERE employee_no IS NOT NULL
  AND TRIM(employee_no) = '';

UPDATE sys_user
SET employee_no = NULL
WHERE deleted = 1
  AND employee_no IS NOT NULL;

CREATE UNIQUE INDEX uk_sys_user_employee_no
ON sys_user (employee_no);
