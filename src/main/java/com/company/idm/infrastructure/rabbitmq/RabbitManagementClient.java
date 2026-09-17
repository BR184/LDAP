package com.company.idm.infrastructure.rabbitmq;

import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.RabbitmqProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通过 RabbitMQ Management HTTP API 自动编排推送所需资源：交换机、队列、绑定与订阅专属账号。
 * 订阅创建与删除由平台自动完成资源变更，部署与运维均无需人工登录 MQ 控制台操作。
 */
@Slf4j
@Component
public class RabbitManagementClient {

    private final RabbitmqProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String authorizationHeader;

    public RabbitManagementClient(RabbitmqProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        // RabbitMQ 管理接口为明文 HTTP：Java HttpClient 默认尝试 h2c 升级会被对端提前断开（EOF reached while reading），
        // 显式固定 HTTP/1.1，避免订阅开通时消息服务调用失败。
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getManagement().getConnectTimeoutMs()))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        this.authorizationHeader = "Basic " + Base64.getEncoder().encodeToString(
            (properties.getManagement().getUsername() + ":" + properties.getManagement().getPassword())
                .getBytes(StandardCharsets.UTF_8));
    }

    /** 幂等创建持久化主题交换机。 */
    public void ensureTopicExchange(String name) {
        ensureExchange(name, "topic");
    }

    /** 幂等创建持久化扇出交换机。 */
    public void ensureFanoutExchange(String name) {
        ensureExchange(name, "fanout");
    }

    /** 幂等创建持久化队列；arguments 可为空。 */
    public void ensureQueue(String name, Map<String, Object> arguments) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("durable", true);
        body.put("auto_delete", false);
        body.put("arguments", arguments == null ? Map.of() : arguments);
        send("PUT", "/api/queues/" + encodedVhost() + "/" + encode(name), body, false);
    }

    /** 创建队列到交换机的绑定（重复创建等价于无操作）。 */
    public void bindQueue(String queue, String exchange, String routingKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("routing_key", routingKey);
        body.put("arguments", Map.of());
        send(
            "POST",
            "/api/bindings/" + encodedVhost() + "/e/" + encode(exchange) + "/q/" + encode(queue),
            body,
            false
        );
    }

    /** 删除队列，同时清理其全部绑定（队列不存在时忽略）。 */
    public void deleteQueue(String name) {
        send("DELETE", "/api/queues/" + encodedVhost() + "/" + encode(name), null, true);
    }

    /** 幂等创建或重置订阅专属账号，账号不授予任何管理标签。 */
    public void createUser(String username, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("password", password);
        body.put("tags", "");
        send("PUT", "/api/users/" + encode(username), body, false);
    }

    /** 删除订阅专属账号（账号不存在时忽略）。 */
    public void deleteUser(String username) {
        send("DELETE", "/api/users/" + encode(username), null, true);
    }

    /** 设置账号在 vhost 上的权限；read 为空表示禁止读取任何队列。 */
    public void grantPermissions(String username, String readPattern) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("configure", "");
        body.put("write", "");
        body.put("read", readPattern == null ? "" : readPattern);
        send("PUT", "/api/permissions/" + encodedVhost() + "/" + encode(username), body, false);
    }

    /** 回收账号在 vhost 上的全部权限（权限不存在时忽略）。 */
    public void revokePermissions(String username) {
        send("DELETE", "/api/permissions/" + encodedVhost() + "/" + encode(username), null, true);
    }

    private void ensureExchange(String name, String type) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("durable", true);
        body.put("auto_delete", false);
        send("PUT", "/api/exchanges/" + encodedVhost() + "/" + encode(name), body, false);
    }

    private void send(String method, String path, Object body, boolean allowNotFound) {
        String url = properties.getManagement().getBaseUrl() + path;
        try {
            HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(body),
                    StandardCharsets.UTF_8
                );
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getManagement().getReadTimeoutMs()))
                .header("Authorization", authorizationHeader)
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if ((status >= 200 && status < 300) || (allowNotFound && status == 404)) {
                return;
            }
            log.warn("RabbitMQ management request failed: {} {} -> {} {}", method, url, status, response.body());
            throw new BizException("RABBITMQ_MANAGEMENT_FAILED", "消息服务操作失败，请确认消息服务可用后重试");
        } catch (IOException exception) {
            log.warn("RabbitMQ management request failed: {} {} -> {}", method, url, exception);
            throw new BizException("RABBITMQ_MANAGEMENT_FAILED", "消息服务操作失败，请确认消息服务可用后重试");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("RabbitMQ management request interrupted: {} {}", method, url, exception);
            throw new BizException("RABBITMQ_MANAGEMENT_FAILED", "消息服务操作失败，请确认消息服务可用后重试");
        }
    }

    private String encodedVhost() {
        return encode(properties.getVhost());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
