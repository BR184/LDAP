# 项目进展记录 004 - 全局异常处理实现

## 1. 里程碑说明

本次完成了基于项目设计文档的全局异常处理实现，目标是统一控制层与安全层的异常返回格式，避免将底层异常细节直接暴露给调用方。

完成时间：2026-04-15

## 2. 本次完成内容

### 2.1 控制层全局异常处理

已完善 `GlobalExceptionHandler`，统一处理以下异常场景：

- 业务异常 `BizException`
- 请求体参数校验异常 `MethodArgumentNotValidException`
- 绑定异常 `BindException`
- 约束校验异常 `ConstraintViolationException`
- 参数类型不匹配异常 `MethodArgumentTypeMismatchException`
- 缺少请求参数异常 `MissingServletRequestParameterException`
- 请求体不可读异常 `HttpMessageNotReadableException`
- 请求方法不支持异常 `HttpRequestMethodNotSupportedException`
- 媒体类型不支持异常 `HttpMediaTypeNotSupportedException`
- 资源不存在异常 `NoResourceFoundException`
- 无权限异常 `AccessDeniedException`
- 其他未知异常 `Exception`

### 2.2 安全层统一异常返回

已新增安全层统一 JSON 返回处理：

- `RestAuthenticationEntryPoint`：处理未登录或登录过期场景，返回 401
- `RestAccessDeniedHandler`：处理无权限场景，返回 403

并已在 `SecurityConfig` 中接入。

### 2.3 错误码常量

已新增通用错误码常量类 `ErrorCodeConstants`，用于统一以下错误码：

- `PARAM_INVALID`
- `REQUEST_BODY_INVALID`
- `REQUEST_PARAM_MISSING`
- `REQUEST_METHOD_NOT_SUPPORTED`
- `MEDIA_TYPE_NOT_SUPPORTED`
- `RESOURCE_NOT_FOUND`
- `AUTH_UNAUTHORIZED`
- `AUTH_FORBIDDEN`
- `INTERNAL_ERROR`

## 3. 设计对齐说明

本次实现与设计文档中的异常规范保持一致：

- 使用统一业务异常基类 `BizException`
- 错误码统一管理
- 默认不向前端暴露底层异常堆栈或内部错误细节
- 统一返回标准 `ApiResponse`

## 4. 测试与验证

本次补充并验证了以下异常场景：

- 未携带 token 访问受保护接口，返回 401
- 登录参数校验失败，返回 400
- 创建重复用户触发业务异常，返回 400
- 无权限用户访问受限接口，返回 403

执行命令：

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

测试结果：通过

## 5. 当前效果

当前平台在成功和失败两类场景下都能保持统一的 JSON 返回风格，便于后续前端、网关和审计日志接入时进行一致处理。
