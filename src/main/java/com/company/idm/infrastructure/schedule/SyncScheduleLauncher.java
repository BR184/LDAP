package com.company.idm.infrastructure.schedule;

import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.infrastructure.config.SyncScheduleProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 提供同步任务的轻量定时触发入口。
 * 当前阶段仅做业务内嵌式调度，不做独立调度平台。
 */
@Component
@RequiredArgsConstructor
public class SyncScheduleLauncher {

    private static final String SCHEDULE_OPERATOR = "system-scheduler";

    private final SyncApplicationService syncApplicationService;
    private final SyncScheduleProperties syncScheduleProperties;

    @Scheduled(cron = "${app.sync.schedule.feishu.cron:0 0 2 * * *}")
    public void runFeishuImport() {
        if (!syncScheduleProperties.getFeishu().isEnabled()) {
            return;
        }
        syncApplicationService.executeFeishuUserSync(SCHEDULE_OPERATOR, SyncTriggerMode.SCHEDULED);
    }

    @Scheduled(cron = "${app.sync.schedule.reconcile.cron:0 30 2 * * *}")
    public void runReconcile() {
        if (!syncScheduleProperties.getReconcile().isEnabled()) {
            return;
        }
        syncApplicationService.executeReconcile(
            syncScheduleProperties.getReconcile().isAutoRepair(),
            SCHEDULE_OPERATOR,
            SyncTriggerMode.SCHEDULED
        );
    }
}
