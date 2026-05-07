package com.company.idm.common.enums;

import lombok.Getter;

/**
 * 定义用户在职状态。
 */
@Getter
public enum EmploymentStatus {
    ACTIVE("在职"),
    RESIGNED("离职");

    private final String displayName;

    EmploymentStatus(String displayName) {
        this.displayName = displayName;
    }

    public static EmploymentStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return ACTIVE;
        }
        for (EmploymentStatus value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return value;
            }
        }
        return ACTIVE;
    }
}
