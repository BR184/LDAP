package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.UserPartTimeDepartmentDO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserPartTimeDepartmentMapper extends BaseMapper<UserPartTimeDepartmentDO> {

    @Select("""
        <script>
        SELECT user_id, dept_code, sort_no, gmt_create
        FROM sys_user_part_time_department
        WHERE user_id IN
        <foreach collection="userIds" item="userId" open="(" separator="," close=")">
            #{userId}
        </foreach>
        ORDER BY user_id, sort_no, dept_code
        </script>
        """)
    List<UserPartTimeDepartmentDO> selectByUserIds(@Param("userIds") List<Long> userIds);
}
