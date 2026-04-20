package com.company.idm.application.sync.handler;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.SyncJobExecutionResult;
import com.company.idm.application.sync.SyncJobHandler;
import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.application.sync.feishu.FeishuUserImportResult;
import com.company.idm.application.sync.feishu.FeishuUserImportService;
import com.company.idm.common.enums.SyncJobType;
import com.company.idm.common.enums.SyncRunStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 提供飞书用户导入任务的公共框架处理器。
 * 当前阶段先落地批次、任务和手工触发框架，具体文件解析在后续子任务中补齐。
 */
@Component
@RequiredArgsConstructor
public class FeishuUserImportHandler implements SyncJobHandler {

    private final FeishuUserImportService importService;

    @Override
    public SyncJobType jobType() {
        return SyncJobType.FEISHU_USER_IMPORT;
    }

    @Override
    public SyncJobExecutionResult preview(SyncRequestPayload payload) {
        FeishuUserImportResult result = importService.preview(payload);
        return buildResult(result, true, payload);
    }

    @Override
    public SyncJobExecutionResult execute(SyncRequestPayload payload) {
        FeishuUserImportResult result = importService.execute(payload);
        return buildResult(result, false, payload);
    }

    private SyncJobExecutionResult buildResult(
        FeishuUserImportResult importResult,
        boolean preview,
        SyncRequestPayload payload
    ) {
        String summaryJson = """
            {"frameworkReady":false,"preview":%s,"target":"USER","newCount":%s,"updateCount":%s,"noChangeCount":%s,"diffCount":%s}
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
