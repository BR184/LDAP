UPDATE sys_permission
SET permission_code = 'USER_READ_SELF_AND_SUBORDINATE_TREE',
    permission_name = '用户查询_本人及直属递归下级',
    remark = '用户管理数据范围：本人、直属下级及递归下级'
WHERE permission_code = 'USER_SUBORDINATE_TREE_READ';
