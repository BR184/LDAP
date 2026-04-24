package com.company.idm.domain.user;

/**
 * 定义密码重置通知能力。
 */
public interface PasswordResetNotificationService {

    void sendPasswordResetMail(User user, String rawPassword);
}
