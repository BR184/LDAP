package com.company.idm.application.user;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class InitialPasswordPolicy {

    private static final String FALLBACK_PASSWORD = "123456";
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1\\d{10}$");

    public String resolve(String mobile) {
        if (mobile == null) {
            return FALLBACK_PASSWORD;
        }
        String normalizedMobile = mobile.trim();
        return MOBILE_PATTERN.matcher(normalizedMobile).matches()
            ? normalizedMobile
            : FALLBACK_PASSWORD;
    }
}
