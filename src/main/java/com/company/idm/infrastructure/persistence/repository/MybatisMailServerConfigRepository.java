package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.domain.mail.MailServerConfig;
import com.company.idm.domain.mail.MailServerConfigRepository;
import com.company.idm.infrastructure.persistence.dataobject.MailServerConfigDO;
import com.company.idm.infrastructure.persistence.mapper.MailServerConfigMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的邮件配置仓储。
 */
@Repository
@RequiredArgsConstructor
public class MybatisMailServerConfigRepository implements MailServerConfigRepository {

    private final MailServerConfigMapper mailServerConfigMapper;

    @Override
    public Optional<MailServerConfig> findCurrent() {
        MailServerConfigDO dataObject = mailServerConfigMapper.selectOne(new LambdaQueryWrapper<MailServerConfigDO>()
            .orderByDesc(MailServerConfigDO::getId)
            .last("LIMIT 1"));
        return Optional.ofNullable(dataObject).map(this::toDomain);
    }

    @Override
    public Optional<MailServerConfig> findEnabled() {
        MailServerConfigDO dataObject = mailServerConfigMapper.selectOne(new LambdaQueryWrapper<MailServerConfigDO>()
            .eq(MailServerConfigDO::getEnabled, 1)
            .orderByDesc(MailServerConfigDO::getId)
            .last("LIMIT 1"));
        return Optional.ofNullable(dataObject).map(this::toDomain);
    }

    @Override
    public MailServerConfig save(MailServerConfig config) {
        MailServerConfigDO dataObject = toDataObject(config);
        if (dataObject.getId() == null) {
            dataObject.setCreator("system");
            dataObject.setModifier("system");
            dataObject.setGmtCreate(LocalDateTime.now());
            dataObject.setGmtModified(LocalDateTime.now());
            mailServerConfigMapper.insert(dataObject);
        } else {
            dataObject.setModifier("system");
            dataObject.setGmtModified(LocalDateTime.now());
            mailServerConfigMapper.updateById(dataObject);
        }
        return toDomain(dataObject);
    }

    private MailServerConfig toDomain(MailServerConfigDO dataObject) {
        return MailServerConfig.builder()
            .id(dataObject.getId())
            .sendMode(dataObject.getSendMode())
            .secureMode(dataObject.getSecureMode())
            .host(dataObject.getHost())
            .port(dataObject.getPort())
            .fromAddress(dataObject.getFromAddress())
            .fromName(dataObject.getFromName())
            .authRequired(dataObject.getAuthRequired() != null && dataObject.getAuthRequired() == 1)
            .username(dataObject.getUsername())
            .passwordCiphertext(dataObject.getPasswordCiphertext())
            .enabled(dataObject.getEnabled() != null && dataObject.getEnabled() == 1)
            .remark(dataObject.getRemark())
            .lastTestSuccess(dataObject.getLastTestSuccess() == null ? null : dataObject.getLastTestSuccess() == 1)
            .lastTestAt(dataObject.getLastTestAt())
            .lastTestMessage(dataObject.getLastTestMessage())
            .build();
    }

    private MailServerConfigDO toDataObject(MailServerConfig config) {
        MailServerConfigDO dataObject = new MailServerConfigDO();
        dataObject.setId(config.getId());
        dataObject.setSendMode(config.getSendMode());
        dataObject.setSecureMode(config.getSecureMode());
        dataObject.setHost(config.getHost());
        dataObject.setPort(config.getPort());
        dataObject.setFromAddress(config.getFromAddress());
        dataObject.setFromName(config.getFromName());
        dataObject.setAuthRequired(Boolean.TRUE.equals(config.getAuthRequired()) ? 1 : 0);
        dataObject.setUsername(config.getUsername());
        dataObject.setPasswordCiphertext(config.getPasswordCiphertext());
        dataObject.setEnabled(Boolean.TRUE.equals(config.getEnabled()) ? 1 : 0);
        dataObject.setRemark(config.getRemark());
        dataObject.setLastTestSuccess(config.getLastTestSuccess() == null ? null : (config.getLastTestSuccess() ? 1 : 0));
        dataObject.setLastTestAt(config.getLastTestAt());
        dataObject.setLastTestMessage(config.getLastTestMessage());
        return dataObject;
    }
}
