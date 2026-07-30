package com.company.idm.interfaces.importplan;

public record FieldChangeResponse(
    Long itemId,
    String fieldKey,
    String fieldLabel,
    String beforeValue,
    String afterValue,
    String riskLevel,
    boolean enabled,
    boolean requiresConfirmation,
    boolean confirmed,
    String status,
    String errorMessage
) {
}
