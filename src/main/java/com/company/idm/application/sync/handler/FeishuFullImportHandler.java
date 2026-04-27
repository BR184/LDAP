package com.company.idm.application.sync.handler;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.SyncJobExecutionResult;
import com.company.idm.application.sync.SyncJobHandler;
import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.application.sync.feishu.FeishuFullImportResult;
import com.company.idm.application.sync.feishu.FeishuFullImportService;
import com.company.idm.common.enums.ImportMode;
import com.company.idm.common.enums.SyncJobType;
import com.company.idm.common.enums.SyncRunStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 处理飞书一键导入任务。
 */
@Component
public class FeishuFullImportHandler implements SyncJobHandler {

    private final FeishuFullImportService fullImportService;

    public FeishuFullImportHandler(FeishuFullImportService fullImportService) {
        this.fullImportService = fullImportService;
    }

    @Override
    public SyncJobType jobType() {
        return SyncJobType.FEISHU_FULL_IMPORT;
    }

    @Override
    public SyncJobExecutionResult preview(SyncRequestPayload payload) {
        return execute(payload);
    }

    @Override
    public SyncJobExecutionResult execute(SyncRequestPayload payload) {
        FeishuFullImportResult result = fullImportService.executeFromDocument(
            payload.documentPath(),
            payload.importMode() == null ? ImportMode.SUPPLEMENT : payload.importMode()
        );
        List<SyncDiffPayload> diffs = new ArrayList<>();
        diffs.addAll(result.departmentImportResult().diffs());
        diffs.addAll(result.userImportResult().diffs());
        diffs.addAll(result.alignmentDiffs());
        String summaryJson = """
            {"preview":false,"target":"IMPORT","departmentNewCount":%s,"departmentUpdateCount":%s,"departmentNoChangeCount":%s,"userNewCount":%s,"userUpdateCount":%s,"userNoChangeCount":%s,"alignmentDiffCount":%s,"importMode":"%s"}
            """.formatted(
            result.departmentImportResult().newCount(),
            result.departmentImportResult().updateCount(),
            result.departmentImportResult().noChangeCount(),
            result.userImportResult().newCount(),
            result.userImportResult().updateCount(),
            result.userImportResult().noChangeCount(),
            result.alignmentDiffs().size(),
            payload.importMode()
        );
        return new SyncJobExecutionResult(SyncRunStatus.SUCCESS, summaryJson, null, diffs);
    }
}
