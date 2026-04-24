package com.company.idm.domain.user;

import org.springframework.stereotype.Service;

/**
 * 定义忘记密码频率控制能力。
 */
@Service
public interface PasswordResetThrottleService {

    boolean tryAcquire(String username, String clientIp);
}
