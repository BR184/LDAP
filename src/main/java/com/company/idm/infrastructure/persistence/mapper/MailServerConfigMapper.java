package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.MailServerConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 邮件服务器配置 Mapper。
 */
@Mapper
public interface MailServerConfigMapper extends BaseMapper<MailServerConfigDO> {
}
