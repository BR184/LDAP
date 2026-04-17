package com.company.idm.application.sync.handler;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.SyncJobExecutionResult;
import com.company.idm.application.sync.SyncJobHandler;
import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.common.enums.SyncJobType;
import com.company.idm.common.enums.SyncRunStatus;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 提供飞书部门导入任务的公共框架处理器。
 * 当前阶段先落地批次、任务和手工触发框架，具体文件解析在后续子任务中补齐。
 */
@Component
public class FeishuDepartmentImportHandler implements SyncJobHandler {

    @Override
    public SyncJobType jobType() {
        return SyncJobType.FEISHU_DEPARTMENT_IMPORT;
    }

    @Override
    public SyncJobExecutionResult preview(SyncRequestPayload payload) {
        return buildFrameworkReadyResult(payload, true);
    }

    @Override
    public SyncJobExecutionResult execute(SyncRequestPayload payload) {
        return buildFrameworkReadyResult(payload, false);
    }

    private SyncJobExecutionResult buildFrameworkReadyResult(SyncRequestPayload payload, boolean preview) {
        String summaryJson = """
            {"frameworkReady":true,"preview":%s,"target":"DEPARTMENT","sourceFileName":"%s","message":"飞书部门导入框架已就绪，待接入真实解析逻辑"}
            """.formatted(preview, safe(payload.sourceFileName()));
        return new SyncJobExecutionResult(SyncRunStatus.SUCCESS, summaryJson, null, List.<SyncDiffPayload>of());
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}
