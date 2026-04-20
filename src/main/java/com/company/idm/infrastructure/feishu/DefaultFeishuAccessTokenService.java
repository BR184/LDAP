package com.company.idm.infrastructure.feishu;

import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.FeishuOpenApiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 基于飞书开放平台认证接口实现租户访问令牌获取。
 */
@Service
public class DefaultFeishuAccessTokenService implements FeishuAccessTokenService {

    private final FeishuOpenApiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile String cachedToken;
    private volatile Instant expireAt = Instant.EPOCH;

    public DefaultFeishuAccessTokenService(FeishuOpenApiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
            .build();
    }

    @Override
    public synchronized String getTenantAccessToken() {
        if (!properties.isEnabled()) {
            throw new BizException("FEISHU_API_DISABLED", "飞书开放平台同步未启用");
        }
        if (cachedToken != null && Instant.now().isBefore(expireAt.minusSeconds(120))) {
            return cachedToken;
        }
        if (properties.getAppId() == null || properties.getAppId().isBlank()
            || properties.getAppSecret() == null || properties.getAppSecret().isBlank()) {
            throw new BizException("FEISHU_ACCESS_TOKEN_FAILED", "飞书应用凭证未配置");
        }
        try {
            String requestBody = objectMapper.writeValueAsString(new AuthRequest(properties.getAppId(), properties.getAppSecret()));
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/open-apis/auth/v3/tenant_access_token/internal"))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            if (response.statusCode() >= 300 || root.path("code").asInt(-1) != 0) {
                throw new BizException("FEISHU_ACCESS_TOKEN_FAILED", "获取飞书租户访问令牌失败");
            }
            cachedToken = root.path("tenant_access_token").asText();
            int expireSeconds = root.path("expire").asInt(7200);
            expireAt = Instant.now().plusSeconds(expireSeconds);
            return cachedToken;
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException("FEISHU_ACCESS_TOKEN_FAILED", "获取飞书租户访问令牌失败");
        }
    }

    private record AuthRequest(String app_id, String app_secret) {
    }
}
