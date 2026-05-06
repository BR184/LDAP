package com.company.idm.domain.mail;

import java.util.Optional;

/**
 * 邮件服务器配置仓储。
 */
public interface MailServerConfigRepository {

    Optional<MailServerConfig> findCurrent();

    Optional<MailServerConfig> findEnabled();

    MailServerConfig save(MailServerConfig config);
}
