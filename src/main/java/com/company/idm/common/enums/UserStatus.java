package com.company.idm.common.enums;

import lombok.Getter;

/**
 * 定义用户启用状态枚举，并提供状态码转换能力。
 */
@Getter
public enum UserStatus {
    DISABLED(0),
    ENABLED(1);

    private final int code;

    UserStatus(int code) {
        this.code = code;
    }

    public static UserStatus fromCode(Integer code) {
        if (code == null) {
            return DISABLED;
        }
        for (UserStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return DISABLED;
    }
}

