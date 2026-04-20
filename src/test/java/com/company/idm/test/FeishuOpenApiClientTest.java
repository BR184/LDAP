package com.company.idm.test;

import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.FeishuOpenApiProperties;
import com.company.idm.infrastructure.feishu.FeishuAccessTokenService;
import com.company.idm.infrastructure.feishu.FeishuOpenApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证飞书开放平台 HTTP 客户端的单元测试。
 */
class FeishuOpenApiClientTest {

    @Test
    void shouldSendAuthorizedGetRequestWithEncodedQueryString() throws Exception {
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        AtomicReference<String> requestQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sample", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestQuery.set(exchange.getRequestURI().getRawQuery());
            writeJson(exchange, 200, "{\"code\":0,\"data\":{\"ok\":true}}");
        });
        server.start();
        try {
            FeishuOpenApiClient client = new FeishuOpenApiClient(
                buildProperties(server),
                () -> "tenant-token",
                new ObjectMapper()
            );

            JsonNode response = client.get("/sample", Map.of("keyword", "研发 中心", "blank", ""));

            assertThat(response.path("data").path("ok").asBoolean()).isTrue();
            assertThat(authorizationHeader.get()).isEqualTo("Bearer tenant-token");
            assertThat(requestQuery.get()).contains("keyword=%E7%A0%94%E5%8F%91+%E4%B8%AD%E5%BF%83");
            assertThat(requestQuery.get()).doesNotContain("blank=");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectWhenRemoteApiReturnsBusinessFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sample", exchange -> writeJson(exchange, 200, "{\"code\":999,\"msg\":\"failed\"}"));
        server.start();
        try {
            FeishuOpenApiClient client = new FeishuOpenApiClient(
                buildProperties(server),
                tokenService(),
                new ObjectMapper()
            );

            assertThatThrownBy(() -> client.get("/sample", Map.of()))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("FEISHU_API_REQUEST_FAILED");
        } finally {
            server.stop(0);
        }
    }

    private FeishuOpenApiProperties buildProperties(HttpServer server) {
        FeishuOpenApiProperties properties = new FeishuOpenApiProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setConnectTimeoutMs(1000);
        properties.setReadTimeoutMs(1000);
        return properties;
    }

    private FeishuAccessTokenService tokenService() {
        return () -> "tenant-token";
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
