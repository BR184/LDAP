package com.company.idm.infrastructure.feishu;

import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.FeishuOpenApiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.stereotype.Component;

/**
 * 提供飞书开放平台的基础 HTTP 调用能力。
 */
@Component
public class FeishuOpenApiClient {

    private final FeishuOpenApiProperties properties;
    private final FeishuAccessTokenService accessTokenService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FeishuOpenApiClient(
        FeishuOpenApiProperties properties,
        FeishuAccessTokenService accessTokenService,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.accessTokenService = accessTokenService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
            .build();
    }

    public JsonNode get(String path, Map<String, String> queryParameters) {
        try {
            String url = buildUrl(path, queryParameters);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("Authorization", "Bearer " + accessTokenService.getTenantAccessToken())
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            if (response.statusCode() >= 300 || root.path("code").asInt(-1) != 0) {
                throw new BizException("FEISHU_API_REQUEST_FAILED", "飞书开放平台请求失败");
            }
            return root;
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException("FEISHU_API_REQUEST_FAILED", "飞书开放平台请求失败");
        }
    }

    private String buildUrl(String path, Map<String, String> queryParameters) {
        if (queryParameters == null || queryParameters.isEmpty()) {
            return properties.getBaseUrl() + path;
        }
        StringJoiner joiner = new StringJoiner("&");
        queryParameters.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                joiner.add(encode(key) + "=" + encode(value));
            }
        });
        String query = joiner.toString();
        return query.isBlank() ? properties.getBaseUrl() + path : properties.getBaseUrl() + path + "?" + query;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
