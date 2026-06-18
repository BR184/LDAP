package com.company.idm.interfaces.importplan;

import java.util.List;

public record ImportBatchDetailResponse(
    ImportBatchResponse batch,
    List<ChangeItemResponse> changeItems,
    List<RollbackItemResponse> rollbackItems
) {
}
