package com.company.idm.domain.mail;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 邮件服务器配置领域对象。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MailServerConfig {

    private Long id;
    private String sendMode;
    private String secureMode;
    private String host;
    private Integer port;
    private String fromAddress;
    private String fromName;
    private Boolean authRequired;
    private String username;
    private String passwordCiphertext;
    private Boolean enabled;
    private String remark;
    private Boolean lastTestSuccess;
    private LocalDateTime lastTestAt;
    private String lastTestMessage;
}
