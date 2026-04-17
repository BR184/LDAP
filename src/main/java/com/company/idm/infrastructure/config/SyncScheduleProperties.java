package com.company.idm.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定同步任务调度相关配置项。
 */
@ConfigurationProperties(prefix = "app.sync.schedule")
public class SyncScheduleProperties {

    private JobProperties feishu = new JobProperties();
    private ReconcileJobProperties reconcile = new ReconcileJobProperties();

    public JobProperties getFeishu() {
        return feishu;
    }

    public void setFeishu(JobProperties feishu) {
        this.feishu = feishu;
    }

    public ReconcileJobProperties getReconcile() {
        return reconcile;
    }

    public void setReconcile(ReconcileJobProperties reconcile) {
        this.reconcile = reconcile;
    }

    public static class JobProperties {
        private boolean enabled;
        private String cron = "0 0 2 * * *";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }

    public static class ReconcileJobProperties extends JobProperties {
        private boolean autoRepair = true;

        public boolean isAutoRepair() {
            return autoRepair;
        }

        public void setAutoRepair(boolean autoRepair) {
            this.autoRepair = autoRepair;
        }
    }
}
