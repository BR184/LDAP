package com.company.idm.application.sync.feishu;

import com.company.idm.application.sync.SyncDiffPayload;
import java.util.List;

/**
 * 封装飞书用户导入执行结果。
 */
public record FeishuUserImportResult(
    int newCount,
    int updateCount,
    int noChangeCount,
    List<SyncDiffPayload> diffs
) {
}
