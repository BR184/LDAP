package com.company.idm.test;

import com.company.idm.common.log.TraceIdConstants;
import com.company.idm.infrastructure.logging.TraceIdFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 traceId 过滤器行为的单元测试。
 */
class TraceIdFilterTest {

    private final TraceIdFilter traceIdFilter = new TraceIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldReuseIncomingTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdConstants.HEADER_NAME, "trace-001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (servletRequest, servletResponse) ->
            assertThat(MDC.get(TraceIdConstants.MDC_KEY)).isEqualTo("trace-001");

        traceIdFilter.doFilter(request, response, filterChain);

        assertThat(response.getHeader(TraceIdConstants.HEADER_NAME)).isEqualTo("trace-001");
        assertThat(MDC.get(TraceIdConstants.MDC_KEY)).isNull();
    }

    @Test
    void shouldGenerateTraceIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceIdFilter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertThat(response.getHeader(TraceIdConstants.HEADER_NAME)).isNotBlank();
    }
}
