package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.DepartmentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供部门表的 MyBatis-Plus 访问能力。
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<DepartmentDO> {
}

