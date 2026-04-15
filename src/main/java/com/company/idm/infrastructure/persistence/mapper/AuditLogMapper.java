package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.AuditLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供审计日志表的 MyBatis-Plus 访问能力。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogDO> {
}
