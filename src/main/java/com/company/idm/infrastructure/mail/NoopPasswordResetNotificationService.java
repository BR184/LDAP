package com.company.idm.infrastructure.mail;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.user.PasswordResetNotificationService;
import com.company.idm.domain.user.User;

/**
 * 在邮件服务未配置时提供显式失败提示。
 */
public class NoopPasswordResetNotificationService implements PasswordResetNotificationService {

    @Override
    public void sendPasswordResetMail(User user, String rawPassword) {
        throw new BizException("MAIL_NOT_CONFIGURED", "邮件服务未配置，无法发送密码重置邮件");
    }
}
