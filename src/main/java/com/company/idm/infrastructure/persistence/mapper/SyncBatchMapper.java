package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.SyncBatchDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供同步批次表的 MyBatis-Plus 访问能力。
 */
@Mapper
public interface SyncBatchMapper extends BaseMapper<SyncBatchDO> {
}
