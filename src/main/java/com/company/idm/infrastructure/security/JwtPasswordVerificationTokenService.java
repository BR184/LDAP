package com.company.idm.infrastructure.security;

import com.company.idm.application.user.PasswordVerificationTokenService;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.user.User;
import com.company.idm.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 基于 JWT 签发短时密码验证凭证，用于将旧密码校验前置到确认修改之前。
 */
@Service
@RequiredArgsConstructor
public class JwtPasswordVerificationTokenService implements PasswordVerificationTokenService {

    private static final String PURPOSE = "PASSWORD_VERIFIED";

    private final JwtProperties jwtProperties;

    @Override
    public String generate(User user) {
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);
        return Jwts.builder()
            .subject(user.getUserId())
            .claim("id", user.getId())
            .claim("tokenVersion", user.getTokenVersion())
            .claim("purpose", PURPOSE)
            .expiration(Date.from(expiresAt))
            .signWith(secretKey())
            .compact();
    }

    @Override
    public void verify(String token, User user) {
        if (token == null || token.isBlank()) {
            throw new BizException("PASSWORD_VERIFICATION_REQUIRED", "请先验证旧密码");
        }
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey()).build()
                .parseSignedClaims(token)
                .getPayload();
            Number id = claims.get("id", Number.class);
            Number tokenVersion = claims.get("tokenVersion", Number.class);
            String purpose = claims.get("purpose", String.class);
            if (!PURPOSE.equals(purpose)
                || id == null
                || tokenVersion == null
                || user.getId() == null
                || !user.getId().equals(id.longValue())
                || !user.getUserId().equals(claims.getSubject())
                || !user.getTokenVersion().equals(tokenVersion.intValue())) {
                throw new BizException("PASSWORD_VERIFICATION_INVALID", "旧密码验证已失效，请重新验证");
            }
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BizException("PASSWORD_VERIFICATION_INVALID", "旧密码验证已失效，请重新验证");
        }
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
