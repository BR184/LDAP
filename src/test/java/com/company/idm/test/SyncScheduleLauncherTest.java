package com.company.idm.test;

import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.infrastructure.config.SyncScheduleProperties;
import com.company.idm.infrastructure.schedule.SyncScheduleLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 验证同步定时触发器行为的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class SyncScheduleLauncherTest {

    @Mock
    private SyncApplicationService syncApplicationService;

    @Test
    void shouldTriggerScheduledFeishuImportWhenEnabled() {
        SyncScheduleProperties properties = new SyncScheduleProperties();
        properties.getFeishu().setEnabled(true);
        SyncScheduleLauncher launcher = new SyncScheduleLauncher(syncApplicationService, properties);

        launcher.runFeishuImport();

        verify(syncApplicationService).executeFeishu(null, null, "system-scheduler", SyncTriggerMode.SCHEDULED);
    }

    @Test
    void shouldSkipScheduledReconcileWhenDisabled() {
        SyncScheduleProperties properties = new SyncScheduleProperties();
        properties.getReconcile().setEnabled(false);
        SyncScheduleLauncher launcher = new SyncScheduleLauncher(syncApplicationService, properties);

        launcher.runReconcile();

        verify(syncApplicationService, never()).executeReconcile(org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }
}
