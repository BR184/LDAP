package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.RoleMenuDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供角色菜单关系表的 MyBatis-Plus 访问能力。
 */
@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenuDO> {
}

