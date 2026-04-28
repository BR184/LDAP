UPDATE sys_menu
SET menu_name = '同步任务',
    path = '/sync/jobs',
    component = 'sync/job/index',
    remark = '同步任务菜单',
    modifier = 'system'
WHERE menu_code = 'OPERATION_LOG';
