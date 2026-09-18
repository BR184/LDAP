package com.company.idm.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 角色供给（订阅推送）部署配置。
 *
 * <p>来源标识是下游平台识别“事实来自哪个身份中台实例”的稳定身份，切换实例时下游
 * 不得因订阅数字标识相同而继承检查点与授权，因此必须由部署显式配置且长期稳定。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.role-supply")
public class RoleSupplyProperties {

    /** 协议版本：消息与接口响应携带，消费方据此判断能力与兼容性。 */
    private int protocolVersion = 2;

    /** 稳定来源标识（身份中台实例），部署时必须显式配置。 */
    private String sourceId = "corp-idm";

    /** 状态心跳间隔（毫秒）：同一专属队列推送权威状态与已提交版本头。 */
    private long heartbeatIntervalMs = 30_000L;

    /** 事件保留天数：超窗后增量不可恢复，消费方必须重建快照。 */
    private int retentionDays = 30;

    /** 保留事件清理批量上限。 */
    private int retentionCleanupBatchSize = 2000;

    /** 完整快照单页角色数上限；大快照分批传输且所有批次属于同一冻结版本。 */
    private int snapshotPageSize = 200;
}
