package com.company.idm.infrastructure.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.RabbitmqProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RabbitManagementClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<RecordedRequest> requests = new ArrayList<>();
    private HttpServer server;
    private int nextStatus = 204;
    private RabbitManagementClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        RabbitmqProperties properties = new RabbitmqProperties();
        properties.getManagement().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getManagement().setUsername("idm");
        properties.getManagement().setPassword("idm-secret");
        client = new RabbitManagementClient(properties, objectMapper);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void createUserSendsPasswordWithEmptyManagementTags() throws Exception {
        client.createUser("idm-sub-3", "mq-password");

        RecordedRequest request = requests.get(0);
        assertThat(request.method()).isEqualTo("PUT");
        assertThat(request.path()).isEqualTo("/api/users/idm-sub-3");
        assertThat(request.authorization()).isEqualTo(
            "Basic " + Base64.getEncoder().encodeToString("idm:idm-secret".getBytes(StandardCharsets.UTF_8))
        );
        JsonNode body = objectMapper.readTree(request.body());
        assertThat(body.get("password").asText()).isEqualTo("mq-password");
        assertThat(body.get("tags").asText()).isEmpty();
    }

    @Test
    void grantPermissionsSendsTheExactReadPatternAndDeniesEverythingElse() throws Exception {
        client.grantPermissions("idm-sub-3", "^\\Qidm.sub.3\\E$");

        RecordedRequest request = requests.get(0);
        assertThat(request.path()).isEqualTo("/api/permissions/%2F/idm-sub-3");
        JsonNode body = objectMapper.readTree(request.body());
        assertThat(body.get("read").asText()).isEqualTo("^\\Qidm.sub.3\\E$");
        assertThat(body.get("configure").asText()).isEmpty();
        assertThat(body.get("write").asText()).isEmpty();
    }

    @Test
    void ensureTopicExchangeDeclaresADurableTopic() throws Exception {
        client.ensureTopicExchange("idm.role-change");

        RecordedRequest request = requests.get(0);
        assertThat(request.path()).isEqualTo("/api/exchanges/%2F/idm.role-change");
        JsonNode body = objectMapper.readTree(request.body());
        assertThat(body.get("type").asText()).isEqualTo("topic");
        assertThat(body.get("durable").asBoolean()).isTrue();
        assertThat(body.get("auto_delete").asBoolean()).isFalse();
    }

    @Test
    void ensureQueueDeclaresADurableQueueWithDeadLetterArguments() throws Exception {
        client.ensureQueue("idm.sub.3", Map.of("x-dead-letter-exchange", "idm.role-change.dlx"));

        RecordedRequest request = requests.get(0);
        assertThat(request.path()).isEqualTo("/api/queues/%2F/idm.sub.3");
        JsonNode body = objectMapper.readTree(request.body());
        assertThat(body.get("durable").asBoolean()).isTrue();
        assertThat(body.get("auto_delete").asBoolean()).isFalse();
        assertThat(body.get("arguments").get("x-dead-letter-exchange").asText())
            .isEqualTo("idm.role-change.dlx");
    }

    @Test
    void bindQueueRoutesByRoleCode() throws Exception {
        client.bindQueue("idm.sub.3", "idm.role-change", "role.finance.reader");

        RecordedRequest request = requests.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/api/bindings/%2F/e/idm.role-change/q/idm.sub.3");
        JsonNode body = objectMapper.readTree(request.body());
        assertThat(body.get("routing_key").asText()).isEqualTo("role.finance.reader");
    }

    @Test
    void deletingMissingResourcesIsTolerated() {
        nextStatus = 404;

        client.deleteQueue("idm.sub.9");
        client.deleteUser("idm-sub-9");

        assertThat(requests).hasSize(2);
    }

    @Test
    void serverFailuresSurfaceAsBizException() {
        nextStatus = 500;

        assertThatThrownBy(() -> client.createUser("idm-sub-3", "mq-password"))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("RABBITMQ_MANAGEMENT_FAILED");
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        requests.add(new RecordedRequest(
            exchange.getRequestMethod(),
            exchange.getRequestURI().getRawPath(),
            exchange.getRequestHeaders().getFirst("Authorization"),
            new String(body, StandardCharsets.UTF_8)
        ));
        if (nextStatus >= 400) {
            byte[] payload = "{\"error\":\"failed\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(nextStatus, payload.length);
            exchange.getResponseBody().write(payload);
        } else {
            exchange.sendResponseHeaders(nextStatus, -1);
        }
        exchange.close();
    }

    private record RecordedRequest(String method, String path, String authorization, String body) {
    }
}
