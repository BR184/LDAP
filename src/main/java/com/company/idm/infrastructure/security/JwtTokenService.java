package com.company.idm.infrastructure.security;

import com.company.idm.application.auth.LoginResult;
import com.company.idm.application.auth.ParsedToken;
import com.company.idm.application.auth.TokenService;
import com.company.idm.domain.user.User;
import com.company.idm.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 基于 JWT 生成和解析平台访问令牌。
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService implements TokenService {

    private final JwtProperties jwtProperties;

    @Override
    public LoginResult generate(User user, Set<String> roleCodes) {
        Instant expiresAt = Instant.now().plus(jwtProperties.getExpireMinutes(), ChronoUnit.MINUTES);
        String token = Jwts.builder()
            .subject(user.getUsername())
            .claim("uid", user.getId())
            .claim("tokenVersion", user.getTokenVersion())
            .claim("roles", roleCodes.stream().sorted().toList())
            .expiration(Date.from(expiresAt))
            .signWith(secretKey())
            .compact();
        return new LoginResult(user.getId(), user.getUsername(), roleCodes, token, expiresAt);
    }

    @Override
    public ParsedToken parse(String token) {
        Claims claims = Jwts.parser().verifyWith(secretKey()).build()
            .parseSignedClaims(token)
            .getPayload();
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        Number tokenVersion = claims.get("tokenVersion", Number.class);
        Number userId = claims.get("uid", Number.class);
        return new ParsedToken(
            userId.longValue(),
            claims.getSubject(),
            tokenVersion.intValue(),
            roles == null ? Set.of() : Set.copyOf(roles),
            claims.getExpiration().toInstant()
        );
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}

