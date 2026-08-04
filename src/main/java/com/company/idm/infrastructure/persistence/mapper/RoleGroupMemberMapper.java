package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.RoleGroupMemberDO;
import com.company.idm.infrastructure.persistence.record.RoleGroupMemberRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleGroupMemberMapper extends BaseMapper<RoleGroupMemberDO> {

    @Select("""
        SELECT member.group_id AS groupId,
               member.user_id AS userId,
               user.real_name AS realName,
               member.member_role AS memberRole
        FROM sys_role_group_member member
        INNER JOIN sys_user user ON user.id = member.user_id AND user.deleted = 0
        WHERE member.group_id = #{groupId}
        ORDER BY CASE member.member_role WHEN 'OWNER' THEN 0 ELSE 1 END, user.real_name, user.id
        """)
    List<RoleGroupMemberRecord> selectMembers(Long groupId);
}
