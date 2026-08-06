package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.RoleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 提供角色表的 MyBatis-Plus 访问能力。
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleDO> {

    @Select("SELECT * FROM sys_role WHERE id = #{id} FOR UPDATE")
    RoleDO selectByIdForUpdate(@Param("id") Long id);
}

