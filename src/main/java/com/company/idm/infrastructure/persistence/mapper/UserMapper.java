package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.UserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 提供用户表的 MyBatis-Plus 访问能力。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {

    @Select("SELECT * FROM sys_user WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    UserDO selectByIdForUpdate(@Param("id") Long id);
}

