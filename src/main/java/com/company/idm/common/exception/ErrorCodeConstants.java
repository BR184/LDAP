package com.company.idm.common.exception;

/**
 * 定义全局异常处理使用的通用错误码常量。
 */
public final class ErrorCodeConstants {

    public static final String PARAM_INVALID = "PARAM_INVALID";
    public static final String REQUEST_BODY_INVALID = "REQUEST_BODY_INVALID";
    public static final String REQUEST_PARAM_MISSING = "REQUEST_PARAM_MISSING";
    public static final String REQUEST_METHOD_NOT_SUPPORTED = "REQUEST_METHOD_NOT_SUPPORTED";
    public static final String MEDIA_TYPE_NOT_SUPPORTED = "MEDIA_TYPE_NOT_SUPPORTED";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String AUTH_UNAUTHORIZED = "AUTH_UNAUTHORIZED";
    public static final String AUTH_FORBIDDEN = "AUTH_FORBIDDEN";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodeConstants() {
    }
}

