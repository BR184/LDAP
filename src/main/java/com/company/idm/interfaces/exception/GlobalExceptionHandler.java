package com.company.idm.interfaces.exception;

import com.company.idm.common.api.ApiResponse;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.common.exception.BizException;
import com.company.idm.common.exception.ErrorCodeConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一处理控制层异常并转换为标准响应。
 * 根据请求 URI 前缀分支：/api/v2/** 返回 V2 响应体（含 traceId），其余返回 V1 响应体。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<?> handleBizException(BizException exception, HttpServletRequest request) {
        HttpStatus status = resolveBizStatus(exception.getCode());
        log.warn("Business exception on [{} {}], code={}, message={}",
            request.getMethod(), request.getRequestURI(), exception.getCode(), exception.getMessage());
        return buildResponse(request, status, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        String message = extractBindingMessage(exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("请求参数错误"));
        log.warn("Method argument validation failed on [{} {}], message={}",
            request.getMethod(), request.getRequestURI(), message);
        return buildResponse(request, HttpStatus.BAD_REQUEST, ErrorCodeConstants.PARAM_INVALID, message);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<?> handleBindException(BindException exception, HttpServletRequest request) {
        String message = extractBindingMessage(exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("请求参数错误"));
        log.warn("Bind exception on [{} {}], message={}", request.getMethod(), request.getRequestURI(), message);
        return buildResponse(request, HttpStatus.BAD_REQUEST, ErrorCodeConstants.PARAM_INVALID, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolationException(
        ConstraintViolationException exception,
        HttpServletRequest request
    ) {
        String message = exception.getConstraintViolations().stream()
            .findFirst()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .orElse("请求参数错误");
        log.warn("Constraint violation on [{} {}], message={}",
            request.getMethod(), request.getRequestURI(), message);
        return buildResponse(request, HttpStatus.BAD_REQUEST, ErrorCodeConstants.PARAM_INVALID, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleMethodArgumentTypeMismatchException(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        String message = "参数 " + exception.getName() + " 类型错误";
        log.warn("Method argument type mismatch on [{} {}], message={}",
            request.getMethod(), request.getRequestURI(), message);
        return buildResponse(request, HttpStatus.BAD_REQUEST, ErrorCodeConstants.PARAM_INVALID, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingServletRequestParameterException(
        MissingServletRequestParameterException exception,
        HttpServletRequest request
    ) {
        String message = "缺少必要参数: " + exception.getParameterName();
        log.warn("Missing request parameter on [{} {}], message={}",
            request.getMethod(), request.getRequestURI(), message);
        return buildResponse(request, HttpStatus.BAD_REQUEST, ErrorCodeConstants.REQUEST_PARAM_MISSING, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleHttpMessageNotReadableException(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        log.warn("Request body unreadable on [{} {}]", request.getMethod(), request.getRequestURI());
        return buildResponse(request, HttpStatus.BAD_REQUEST, ErrorCodeConstants.REQUEST_BODY_INVALID, "请求体格式错误");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleHttpRequestMethodNotSupportedException(
        HttpRequestMethodNotSupportedException exception,
        HttpServletRequest request
    ) {
        String message = "不支持的请求方法: " + exception.getMethod();
        log.warn("Request method not supported on [{} {}], message={}",
            request.getMethod(), request.getRequestURI(), message);
        return buildResponse(request, HttpStatus.METHOD_NOT_ALLOWED, ErrorCodeConstants.REQUEST_METHOD_NOT_SUPPORTED, message);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> handleHttpMediaTypeNotSupportedException(
        HttpMediaTypeNotSupportedException exception,
        HttpServletRequest request
    ) {
        String message = "不支持的请求类型: " + exception.getContentType();
        log.warn("Media type not supported on [{} {}], message={}",
            request.getMethod(), request.getRequestURI(), message);
        return buildResponse(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCodeConstants.MEDIA_TYPE_NOT_SUPPORTED, message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFoundException(
        NoResourceFoundException exception,
        HttpServletRequest request
    ) {
        String message = "请求的资源不存在";
        log.warn("Resource not found on [{} {}]", request.getMethod(), request.getRequestURI());
        return buildResponse(request, HttpStatus.NOT_FOUND, ErrorCodeConstants.RESOURCE_NOT_FOUND, message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDeniedException(
        AccessDeniedException exception,
        HttpServletRequest request
    ) {
        log.warn("Access denied on [{} {}], message={}",
            request.getMethod(), request.getRequestURI(), exception.getMessage());
        return buildResponse(request, HttpStatus.FORBIDDEN, ErrorCodeConstants.AUTH_FORBIDDEN, "无权访问当前资源");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleOtherException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception on [{} {}]", request.getMethod(), request.getRequestURI(), exception);
        return buildResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeConstants.INTERNAL_ERROR, "系统内部异常，请联系管理员");
    }

    /**
     * 按请求 URI 分支返回 V1/V2 统一响应体。
     */
    private ResponseEntity<?> buildResponse(HttpServletRequest request, HttpStatus status, String code, String message) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/v2")) {
            return ResponseEntity.status(status).body(ApiResponseV2.failure(code, message));
        }
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message));
    }

    /**
     * 根据业务错误码推导控制层 HTTP 状态码。
     * 该映射保持最小集实现，未显式声明的业务异常统一按 400 处理。
     */
    private HttpStatus resolveBizStatus(String code) {
        if (code == null || code.isBlank()) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code.endsWith("_NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        }
        return switch (code) {
            case "AUTH_INVALID", ErrorCodeConstants.AUTH_UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case "AUTH_DISABLED", ErrorCodeConstants.AUTH_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case "USER_ROLE_ASSIGN_CONFLICT", "ROLE_PERMISSION_ASSIGN_CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    /**
     * 统一兜底参数异常消息，避免出现空消息或不稳定输出。
     */
    private String extractBindingMessage(String message) {
        if (message == null || message.isBlank()) {
            return "请求参数错误";
        }
        return message;
    }
}
