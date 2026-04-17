# 项目进展记录 019 - 同步公共模型与触发框架落地

## 1. 里程碑说明

本次完成了第二期“同步公共模型与手工/定时触发框架”的基础代码落地，使飞书导入、LDAP 对账和后续补偿修复能够共享统一的批次、任务和差异模型。

完成时间：2026-04-17

## 2. 本次完成内容

### 2.1 同步公共模型落地

已新增并落地：

- `SyncBatch`
- `SyncJob`
- `SyncDiff`

并配套完成：

- `sys_sync_batch`
- `sys_sync_job`
- `sys_sync_diff`

当前同步框架支持记录：

- 批次类型
- 触发方式
- 任务状态
- 请求快照
- 结果快照
- 差异项
- 关联批次

### 2.2 同步应用服务落地

已新增：

- `SyncApplicationService`

当前已支持：

- 飞书同步预览
- 飞书同步执行
- LDAP 对账预览
- LDAP 对账执行
- 同步任务重试
- 同步任务列表查询
- 同步批次详情查询

### 2.3 手工触发与定时触发

已新增：

- `SyncController`
- `SyncScheduleLauncher`

当前支持两种触发方式：

- `MANUAL`
- `SCHEDULED`

并遵循同一套执行链路：

1. 创建批次
2. 创建任务
3. 执行处理器
4. 记录差异
5. 汇总结果
6. 写审计日志

### 2.4 已落地处理器

当前已补充：

- `FeishuDepartmentImportHandler`
- `FeishuUserImportHandler`
- `LdapReconcileDepartmentHandler`
- `LdapReconcileUserHandler`

说明：

- 飞书导入处理器当前先完成公共框架占位，真实数据包解析逻辑将在后续子任务中补齐
- LDAP 对账处理器当前已具备基础扫描能力，并支持部门维度与用户维度的基础修复

## 3. 配套补充

本次同时补充：

- 同步相关权限点与管理员默认授权
- 调度配置项 `app.sync.schedule.*`
- `@EnableScheduling`

## 4. 测试验证

本次新增并补充了以下测试：

- `SyncApplicationServiceTest`
- `LdapReconcileDepartmentHandlerTest`
- `SyncScheduleLauncherTest`
- `PrototypeIntegrationTest` 中的同步接口集成测试

本次改动后已重新执行全量测试，并通过。

执行命令：

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

测试结果：

- Tests run: `79`
- Failures: `0`
- Errors: `0`

## 5. 文档更新

已同步更新：

- `docs/project-design.md`

主要补充内容包括：

- 同步公共模型设计
- 同步表结构说明
- 同步接口当前已落地清单
- 第二期同步公共底座已完成项
- 最新测试结果

## 6. 下一步建议

下一轮建议继续推进：

1. 飞书部门导入的真实文件解析逻辑
2. 飞书用户导入的真实文件解析逻辑
3. 更细粒度的 LDAP 关系对账
4. 导入结果页面与对账结果页面前端预留
