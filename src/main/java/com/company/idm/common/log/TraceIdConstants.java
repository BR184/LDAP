package com.company.idm.common.log;

/**
 * 定义日志链路追踪使用的 traceId 常量。
 */
public final class TraceIdConstants {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER_NAME = "X-Trace-Id";

    private TraceIdConstants() {
    }
}

