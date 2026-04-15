package com.company.idm.common.exception;

import lombok.Getter;

/**
 * 平台统一业务异常，封装错误码与错误信息。
 */
@Getter
public class BizException extends RuntimeException {

    private final String code;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }
}

