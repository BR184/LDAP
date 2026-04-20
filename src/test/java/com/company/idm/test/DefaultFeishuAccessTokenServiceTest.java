package com.company.idm.test;

import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.FeishuOpenApiProperties;
import com.company.idm.infrastructure.feishu.DefaultFeishuAccessTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证飞书租户访问令牌服务的单元测试。
 */
class DefaultFeishuAccessTokenServiceTest {

    @Test
    void shouldCacheTenantAccessTokenWithinValidWindow() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/open-apis/auth/v3/tenant_access_token/internal", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, "{\"code\":0,\"tenant_access_token\":\"token-123\",\"expire\":3600}");
        });
        server.start();
        try {
            FeishuOpenApiProperties properties = buildProperties(server);
            DefaultFeishuAccessTokenService service = new DefaultFeishuAccessTokenService(properties, new ObjectMapper());

            String first = service.getTenantAccessToken();
            String second = service.getTenantAccessToken();

            assertThat(first).isEqualTo("token-123");
            assertThat(second).isEqualTo("token-123");
            assertThat(requestCount.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectWhenFeishuApiDisabled() {
        FeishuOpenApiProperties properties = new FeishuOpenApiProperties();
        properties.setEnabled(false);
        DefaultFeishuAccessTokenService service = new DefaultFeishuAccessTokenService(properties, new ObjectMapper());

        assertThatThrownBy(service::getTenantAccessToken)
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("FEISHU_API_DISABLED");
    }

    @Test
    void shouldRejectWhenCredentialsMissing() {
        FeishuOpenApiProperties properties = new FeishuOpenApiProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1");
        DefaultFeishuAccessTokenService service = new DefaultFeishuAccessTokenService(properties, new ObjectMapper());

        assertThatThrownBy(service::getTenantAccessToken)
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("FEISHU_ACCESS_TOKEN_FAILED");
    }

    private FeishuOpenApiProperties buildProperties(HttpServer server) {
        FeishuOpenApiProperties properties = new FeishuOpenApiProperties();
        properties.setEnabled(true);
        properties.setAppId("cli_xxx");
        properties.setAppSecret("secret_xxx");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setConnectTimeoutMs(1000);
        properties.setReadTimeoutMs(1000);
        return properties;
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
