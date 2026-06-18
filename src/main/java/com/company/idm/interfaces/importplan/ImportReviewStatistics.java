package com.company.idm.interfaces.importplan;

public record ImportReviewStatistics(
    int userRows,
    int departmentRows,
    int createRows,
    int updateRows,
    int resignRows,
    int conflictRows,
    int failedRows,
    int ldapFailedRows
) {
}
