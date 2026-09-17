package com.company.idm.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 角色变更推送的 RabbitMQ 拓扑、管理 API 与发布器配置。
 */
@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitmqProperties {

    private String exchange = "idm.role-change";
    private String deadLetterExchange = "idm.role-change.dlx";
    private String deadLetterQueue = "idm.role-change.dlq";
    private String vhost = "/";
    private final Management management = new Management();
    private final Connection connection = new Connection();
    private final Publisher publisher = new Publisher();

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getDeadLetterExchange() {
        return deadLetterExchange;
    }

    public void setDeadLetterExchange(String deadLetterExchange) {
        this.deadLetterExchange = deadLetterExchange;
    }

    public String getDeadLetterQueue() {
        return deadLetterQueue;
    }

    public void setDeadLetterQueue(String deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
    }

    public String getVhost() {
        return vhost;
    }

    public void setVhost(String vhost) {
        this.vhost = vhost;
    }

    public Management getManagement() {
        return management;
    }

    public Connection getConnection() {
        return connection;
    }

    public Publisher getPublisher() {
        return publisher;
    }

    public static class Management {

        private String baseUrl = "http://localhost:15672";
        private String username = "idm";
        private String password = "idm";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 10000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    public static class Connection {

        private String externalHost = "";
        private int externalPort = 5672;

        public String getExternalHost() {
            return externalHost;
        }

        public void setExternalHost(String externalHost) {
            this.externalHost = externalHost;
        }

        public int getExternalPort() {
            return externalPort;
        }

        public void setExternalPort(int externalPort) {
            this.externalPort = externalPort;
        }
    }

    public static class Publisher {

        private boolean enabled = true;
        private long fixedDelayMs = 5000;
        private long confirmTimeoutMs = 10000;
        private int batchSize = 200;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }

        public long getConfirmTimeoutMs() {
            return confirmTimeoutMs;
        }

        public void setConfirmTimeoutMs(long confirmTimeoutMs) {
            this.confirmTimeoutMs = confirmTimeoutMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
