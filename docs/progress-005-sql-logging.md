# 项目进展记录 005 - SQL 日志能力实现

## 1. 里程碑说明

本次完成了项目的 SQL 日志能力设计与实现，目标是让项目在开发与联调阶段具备可观测的 SQL 执行日志，同时满足生产可控、参数脱敏、慢 SQL 识别和链路关联的要求。

完成时间：2026-04-15

## 2. 本次完成内容

### 2.1 SQL 日志拦截能力

已新增基于 MyBatis 插件机制的 `SqlLogInterceptor`，用于拦截查询与更新操作并记录：

- `sqlId`
- `commandType`
- `costMs`
- 标准化后的 SQL
- 参数摘要
- 结果摘要

### 2.2 慢 SQL 能力

已支持慢 SQL 阈值配置：

- 普通 SQL：按 `INFO` 输出
- 慢 SQL：按 `WARN` 输出，并加上 `[SLOW_SQL]` 标记
- SQL 执行失败：按 `ERROR` 输出

### 2.3 参数脱敏与截断

已实现 SQL 参数日志安全控制：

- 对密码、token、secret、credential 等敏感字段自动脱敏
- 对超长 SQL 做长度截断
- 对超长参数值做长度截断
- 对数组和集合参数做数量限制，避免日志膨胀

### 2.4 traceId 关联

已新增请求级 traceId 能力：

- `TraceIdFilter` 负责从请求头读取或自动生成 `X-Trace-Id`
- traceId 写入 MDC
- traceId 回写到响应头
- `logback-spring.xml` 已将 traceId 加入日志输出格式

### 2.5 审计日志关联

已增强审计日志落库逻辑：

- 当 `AuditLog` 本身未显式传入 traceId 时
- 自动从 MDC 中读取 traceId 写入 `sys_audit_log`

## 3. 配置项

已新增 `app.sql-log` 配置：

- `enabled`
- `slow-sql-threshold-ms`
- `show-parameters`
- `max-sql-length`
- `max-parameter-length`
- `max-collection-length`
- `mask-keywords`

当前原型环境已默认开启 SQL 日志，便于开发联调。

## 4. 设计说明

本次实现遵循的设计原则如下：

- 只在应用层统一输出 SQL 日志，不依赖手工打印
- 通过配置开关控制能力启停，避免生产环境默认噪音
- 不输出明文敏感数据
- 通过 traceId 将 SQL、控制层异常和审计日志串联起来
- 通过慢 SQL 阈值突出真正需要关注的数据库问题

## 5. 测试与验证

本次已补充并通过以下验证：

- SQL 格式化与参数脱敏单元测试
- 集成测试中校验响应头自动返回 `X-Trace-Id`
- 全量测试命令执行通过

执行命令：

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

## 6. 当前效果

当前项目启动后，控制台日志中已经能够看到：

- 含 traceId 的统一日志格式
- 标准化的 SQL 语句
- SQL 参数与执行耗时
- 审计日志入库时自动带 traceId

## 7. 下一步建议

下一轮如果继续完善日志体系，建议优先补充：

1. 基于 profile 的 SQL 日志开关策略
2. 文件日志与滚动归档
3. 慢 SQL 独立 logger 或独立 appender
4. 接口访问日志与操作审计切面
