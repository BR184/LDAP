package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.UserRoleDO;
import com.company.idm.infrastructure.persistence.record.UserRoleBindingRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleDO> {

    @Select("""
        SELECT r.role_code
        FROM sys_user_role ur
        INNER JOIN sys_user u ON ur.user_id = u.id
        INNER JOIN sys_role r ON ur.role_id = r.id
        WHERE u.username = #{username} AND u.deleted = 0 AND r.status = 1
        """)
    List<String> selectRoleCodesByUsername(String username);

    @Select("""
        SELECT u.username AS username, r.role_code AS roleCode
        FROM sys_user_role ur
        INNER JOIN sys_user u ON ur.user_id = u.id
        INNER JOIN sys_role r ON ur.role_id = r.id
        WHERE u.deleted = 0 AND r.status = 1
        """)
    List<UserRoleBindingRecord> selectUserRoleBindings();
}

