package com.company.idm.application.user;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Generate deterministic intranet email addresses.
 */
@Service
@RequiredArgsConstructor
public class IntranetEmailGenerationService {

    private static final String DOMAIN = "@crowncad.com";

    private final UserRepository userRepository;

    public String generate(String uniqueIdentifier, Long currentUserId) {
        String localPart = normalize(uniqueIdentifier);
        if (localPart == null) {
            throw new BizException("USER_INTRANET_EMAIL_GENERATE_FAILED", "内网邮箱生成失败");
        }
        String candidate = localPart + DOMAIN;
        if (isAvailable(candidate, currentUserId)) {
            return candidate;
        }
        int suffix = 2;
        while (suffix < 10000) {
            candidate = localPart + suffix + DOMAIN;
            if (isAvailable(candidate, currentUserId)) {
                return candidate;
            }
            suffix++;
        }
        throw new BizException("USER_INTRANET_EMAIL_GENERATE_FAILED", "内网邮箱生成失败");
    }

    private boolean isAvailable(String intranetEmail, Long currentUserId) {
        User matchedUser = userRepository.findByIntranetEmail(intranetEmail).orElse(null);
        if (matchedUser == null) {
            return true;
        }
        return currentUserId != null && currentUserId.equals(matchedUser.getId());
    }

    private String normalize(String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            return null;
        }
        String normalized = rawIdentifier.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "");
        return normalized.isBlank() ? null : normalized;
    }
}
