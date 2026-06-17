package com.company.idm.infrastructure.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpUtil {

    private ClientIpUtil() {
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "UNKNOWN";
        }
        String forwardedFor = firstHeaderValue(request.getHeader("X-Forwarded-For"));
        if (!forwardedFor.isBlank()) {
            return forwardedFor;
        }
        String realIp = firstHeaderValue(request.getHeader("X-Real-IP"));
        if (!realIp.isBlank()) {
            return realIp;
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? "UNKNOWN" : remoteAddr.trim();
    }

    private static String firstHeaderValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int delimiterIndex = value.indexOf(',');
        return (delimiterIndex >= 0 ? value.substring(0, delimiterIndex) : value).trim();
    }
}
