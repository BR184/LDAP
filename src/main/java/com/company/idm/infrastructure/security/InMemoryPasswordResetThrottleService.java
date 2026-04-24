package com.company.idm.infrastructure.security;

import com.company.idm.domain.user.PasswordResetThrottleService;
import com.company.idm.infrastructure.config.PasswordResetProperties;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 提供单实例内存版忘记密码限流能力。
 */
@Component
@RequiredArgsConstructor
public class InMemoryPasswordResetThrottleService implements PasswordResetThrottleService {

    private final PasswordResetProperties passwordResetProperties;
    private final Map<String, Instant> usernameCooldownIndex = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> ipAttemptIndex = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean tryAcquire(String username, String clientIp) {
        Instant now = Instant.now();
        String normalizedUsername = normalize(username, "UNKNOWN_USER");
        String normalizedIp = normalize(clientIp, "UNKNOWN_IP");

        Instant nextAllowedAt = usernameCooldownIndex.get(normalizedUsername);
        if (nextAllowedAt != null && nextAllowedAt.isAfter(now)) {
            return false;
        }

        Deque<Instant> attempts = ipAttemptIndex.computeIfAbsent(normalizedIp, key -> new ArrayDeque<>());
        Instant earliestAllowed = now.minusSeconds(passwordResetProperties.getIpWindowSeconds());
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(earliestAllowed)) {
            attempts.removeFirst();
        }
        if (attempts.size() >= passwordResetProperties.getIpMaxAttempts()) {
            return false;
        }

        attempts.addLast(now);
        usernameCooldownIndex.put(normalizedUsername, now.plusSeconds(passwordResetProperties.getUsernameCooldownSeconds()));
        return true;
    }

    private String normalize(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
