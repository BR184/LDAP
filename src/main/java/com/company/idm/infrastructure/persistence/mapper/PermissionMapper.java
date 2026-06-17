package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.PermissionDO;
import com.company.idm.infrastructure.persistence.record.RolePolicyRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 提供权限表及角色策略查询能力。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<PermissionDO> {

    @Select("""
        SELECT r.role_code AS roleCode, p.resource_path AS resourcePath, p.action AS action
        FROM sys_role_permission rp
        INNER JOIN sys_role r ON rp.role_id = r.id
        INNER JOIN sys_permission p ON rp.permission_id = p.id
        WHERE r.status = 1 AND p.status = 1
        """)
    List<RolePolicyRecord> selectRolePolicies();

    @Select("""
        SELECT DISTINCT p.permission_code
        FROM sys_user u
        INNER JOIN sys_user_role ur ON ur.user_id = u.id
        INNER JOIN sys_role r ON r.id = ur.role_id
        INNER JOIN sys_role_permission rp ON rp.role_id = r.id
        INNER JOIN sys_permission p ON p.id = rp.permission_id
        WHERE u.user_id = #{userId}
          AND u.deleted = 0
          AND r.status = 1
          AND p.status = 1
        """)
    List<String> selectPermissionCodesByUserId(String userId);
}

