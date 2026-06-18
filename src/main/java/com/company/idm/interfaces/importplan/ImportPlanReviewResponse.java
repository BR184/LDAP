package com.company.idm.interfaces.importplan;

import java.util.List;

public record ImportPlanReviewResponse(
    ImportBatchResponse batch,
    ImportReviewStatistics statistics,
    List<UserReviewRowResponse> userRows,
    List<DepartmentReviewRowResponse> departmentRows,
    List<ConflictReviewRowResponse> conflictRows
) {
}
