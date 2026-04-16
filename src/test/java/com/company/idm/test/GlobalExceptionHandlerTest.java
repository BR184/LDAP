package com.company.idm.test;

import com.company.idm.common.api.ApiResponse;
import com.company.idm.common.exception.BizException;
import com.company.idm.interfaces.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证全局异常处理状态码映射的单元测试。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Test
    void shouldMapNotFoundBizExceptionTo404() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/1");

        var response = globalExceptionHandler.handleBizException(new BizException("USER_NOT_FOUND", "用户不存在"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).extracting(ApiResponse::getCode).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void shouldMapAccessDeniedTo403() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");

        var response = globalExceptionHandler.handleAccessDeniedException(new AccessDeniedException("denied"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).extracting(ApiResponse::getCode).isEqualTo("AUTH_FORBIDDEN");
    }

    @Test
    void shouldHideInternalExceptionMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");

        var response = globalExceptionHandler.handleOtherException(new RuntimeException("database leaked"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).extracting(ApiResponse::getMessage).isEqualTo("系统内部异常，请联系管理员");
    }
}
