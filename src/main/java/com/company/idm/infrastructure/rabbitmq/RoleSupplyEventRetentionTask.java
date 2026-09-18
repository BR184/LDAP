package com.company.idm.infrastructure.rabbitmq;

import com.company.idm.domain.rolegroup.RoleMembershipChangeRepository;
import com.company.idm.infrastructure.config.RoleSupplyProperties;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 角色供给事件保留窗口清理。
 *
 * <p>事件保留窗口决定消费方可恢复的最早检查点：超窗后增量不再可用，消费方必须重建
 * 完整快照，服务端也必须明确告知而不是返回看似成功的残缺增量。保留天数由部署配置管理，
 * 配置为非正数时表示不清理。只清理已发布事件，避免删除尚未投递的事实。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleSupplyEventRetentionTask {

    private final RoleMembershipChangeRepository changeRepository;
    private final RoleSupplyProperties supplyProperties;

    @Scheduled(cron = "${app.role-supply.retention-cleanup-cron:0 30 3 * * *}")
    public void cleanupExpiredEvents() {
        int retentionDays = supplyProperties.getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        int deleted = changeRepository.deleteOlderThan(threshold, supplyProperties.getRetentionCleanupBatchSize());
        if (deleted > 0) {
            log.info("角色供给事件保留窗口清理完成：deleted={}, threshold={}", deleted, threshold);
        }
    }
}
