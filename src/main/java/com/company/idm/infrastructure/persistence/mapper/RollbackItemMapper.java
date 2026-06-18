package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.RollbackItemDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RollbackItemMapper extends BaseMapper<RollbackItemDO> {
}
