package com.company.idm.interfaces.importplan;

import java.util.List;

public record ConfirmImportPlanRequest(
    List<Long> enabledItemIds,
    List<Long> confirmedItemIds
) {
}
