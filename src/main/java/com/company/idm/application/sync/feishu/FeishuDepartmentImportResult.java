package com.company.idm.application.sync.feishu;

import com.company.idm.application.sync.SyncDiffPayload;
import java.util.List;

/**
 * 封装飞书部门导入执行结果。
 */
public record FeishuDepartmentImportResult(
    int newCount,
    int updateCount,
    int noChangeCount,
    List<SyncDiffPayload> diffs
) {
}
