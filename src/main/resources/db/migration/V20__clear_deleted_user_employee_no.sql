UPDATE sys_user
SET employee_no = NULL
WHERE deleted = 1
  AND employee_no IS NOT NULL;
