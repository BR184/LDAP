package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.ChangeItemDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChangeItemMapper extends BaseMapper<ChangeItemDO> {
}
