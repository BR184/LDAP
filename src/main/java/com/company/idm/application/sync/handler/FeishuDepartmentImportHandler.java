package com.company.idm.application.sync.handler;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.SyncJobExecutionResult;
import com.company.idm.application.sync.SyncJobHandler;
import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.application.sync.feishu.FeishuDepartmentImportResult;
import com.company.idm.application.sync.feishu.FeishuDepartmentImportService;
import com.company.idm.common.enums.SyncJobType;
import com.company.idm.common.enums.SyncRunStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 提供飞书部门导入任务的公共框架处理器。
 * 当前阶段先落地批次、任务和手工触发框架，具体文件解析在后续子任务中补齐。
 */
@Component
@RequiredArgsConstructor
public class FeishuDepartmentImportHandler implements SyncJobHandler {

    private final FeishuDepartmentImportService importService;

    @Override
    public SyncJobType jobType() {
        return SyncJobType.FEISHU_DEPARTMENT_IMPORT;
    }

    @Override
    public SyncJobExecutionResult preview(SyncRequestPayload payload) {
        FeishuDepartmentImportResult result = hasDocumentPath(payload)
            ? importService.previewFromDocument(payload.documentPath())
            : importService.preview(payload);
        return buildResult(result, true, payload);
    }

    @Override
    public SyncJobExecutionResult execute(SyncRequestPayload payload) {
        FeishuDepartmentImportResult result = hasDocumentPath(payload)
            ? importService.executeFromDocument(payload.documentPath())
            : importService.execute(payload);
        return buildResult(result, false, payload);
    }

    private boolean hasDocumentPath(SyncRequestPayload payload) {
        return payload.documentPath() != null && !payload.documentPath().isBlank();
    }

    private SyncJobExecutionResult buildResult(
        FeishuDepartmentImportResult importResult,
        boolean preview,
        SyncRequestPayload payload
    ) {
        String summaryJson = """
            {"frameworkReady":false,"preview":%s,"target":"DEPARTMENT","newCount":%s,"updateCount":%s,"noChangeCount":%s,"diffCount":%s}
            """.formatted(
            preview,
            importResult.newCount(),
            importResult.updateCount(),
            importResult.noChangeCount(),
            importResult.diffs().size()
        );
        return new SyncJobExecutionResult(SyncRunStatus.SUCCESS, summaryJson, null, importResult.diffs());
    }
}
