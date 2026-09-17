package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.UserRoleDO;
import com.company.idm.infrastructure.persistence.record.RoleSupplyMemberRecord;
import com.company.idm.infrastructure.persistence.record.UserRoleBindingRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 提供用户角色关系查询与映射能力。
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleDO> {

    @Select("""
        SELECT r.role_code
        FROM sys_user_role ur
        INNER JOIN sys_user u ON ur.user_id = u.id
        INNER JOIN sys_role r ON ur.role_id = r.id
        WHERE u.user_id = #{userId} AND u.deleted = 0 AND r.status = 1
        """)
    List<String> selectRoleCodesByUserId(String userId);

    @Select("""
        <script>
        SELECT u.user_id AS userId, r.role_code AS roleCode
        FROM sys_user_role ur
        INNER JOIN sys_user u ON ur.user_id = u.id
        INNER JOIN sys_role r ON ur.role_id = r.id
        WHERE u.deleted = 0 AND r.status = 1
          AND u.id IN
          <foreach collection="userIds" item="id" open="(" separator="," close=")">
              #{id}
          </foreach>
        </script>
        """)
    List<UserRoleBindingRecord> selectRoleCodesByUserIds(@Param("userIds") List<Long> userIds);

    @Select("""
        SELECT u.user_id AS userId, r.role_code AS roleCode
        FROM sys_user_role ur
        INNER JOIN sys_user u ON ur.user_id = u.id
        INNER JOIN sys_role r ON ur.role_id = r.id
        WHERE u.deleted = 0 AND r.status = 1
        """)
    List<UserRoleBindingRecord> selectUserRoleBindings();

    @Select("""
        SELECT COUNT(1)
        FROM sys_user_role ur
        INNER JOIN sys_user u ON ur.user_id = u.id
        WHERE ur.role_id = #{roleId} AND u.deleted = 0
        """)
    long countActiveBindingsByRoleId(Long roleId);

    @Select("""
        SELECT u.id AS userId, u.real_name AS realName
        FROM sys_user_role ur
        INNER JOIN sys_user u ON u.id = ur.user_id
        WHERE ur.role_id = #{roleId} AND u.deleted = 0
        ORDER BY u.real_name, u.id
        """)
    List<com.company.idm.infrastructure.persistence.record.PublicUserRecord> selectPublicUsersByRoleId(Long roleId);

    @Select("""
        SELECT u.user_id AS platformUserId, u.real_name AS realName
        FROM sys_user_role ur
        INNER JOIN sys_user u ON u.id = ur.user_id
        WHERE ur.role_id = #{roleId} AND u.deleted = 0
        ORDER BY u.real_name, u.id
        """)
    List<RoleSupplyMemberRecord> selectRoleSupplyMembersByRoleId(Long roleId);
}

