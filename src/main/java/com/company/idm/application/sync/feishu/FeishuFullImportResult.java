package com.company.idm.application.sync.feishu;

import com.company.idm.application.sync.SyncDiffPayload;
import java.util.List;

/**
 * 封装一键导入执行结果。
 */
public record FeishuFullImportResult(
    FeishuDepartmentImportResult departmentImportResult,
    FeishuUserImportResult userImportResult,
    List<SyncDiffPayload> alignmentDiffs
) {
}
