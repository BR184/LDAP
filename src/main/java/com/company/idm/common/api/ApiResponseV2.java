package com.company.idm.common.api;

import com.company.idm.common.log.TraceIdConstants;
import org.slf4j.MDC;

/**
 * V2 统一接口响应体。
 * 遵循阿里巴巴 Java 开发手册的统一返回约定：success/code/message/data + traceId。
 * 业务错误码保持字符串三段式（如 USER_NOT_FOUND、PARAM_INVALID），HTTP 状态码表达错误大类。
 */
public record ApiResponseV2<T>(boolean success, String code, String message, T data, String traceId) {

    public static <T> ApiResponseV2<T> ok(T data) {
        return new ApiResponseV2<>(true, "SUCCESS", "OK", data, resolveTraceId());
    }

    public static ApiResponseV2<Void> ok() {
        return ok(null);
    }

    public static ApiResponseV2<Void> failure(String code, String message) {
        return new ApiResponseV2<>(false, code, message, null, resolveTraceId());
    }

    private static String resolveTraceId() {
        return MDC.get(TraceIdConstants.MDC_KEY);
    }
}
