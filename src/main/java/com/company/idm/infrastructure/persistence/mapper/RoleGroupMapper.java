package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.RoleGroupDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleGroupMapper extends BaseMapper<RoleGroupDO> {

    @Select("SELECT id FROM sys_role_group WHERE id = #{groupId} FOR UPDATE")
    Long lockById(Long groupId);
}
