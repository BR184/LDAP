# 项目进展记录 026 - 飞书文件导入回补

## 1. 里程碑说明

本次针对内网开发场景，补回了“按飞书数据文档路径手动导入”的能力，同时保留了原有在线飞书 API 导入和定时调度代码，但将自动拉取默认关闭，避免当前阶段误触发。

完成时间：2026-04-20

## 2. 本次完成内容

### 2.1 飞书文件导入配置

新增配置：

- `app.sync.feishu.file-import.enabled`
- `app.sync.feishu.file-import.root-dir`
- `app.sync.feishu.file-import.max-file-size-bytes`

当前默认策略：

- 手工文件导入开启
- 在线飞书 API 导入代码保留
- 凌晨自动拉取飞书 API 调度默认关闭

### 2.2 文件导入解析能力

新增：

- `FeishuFileImportProperties`
- `FeishuImportDocumentResolver`
- `FeishuFileImportRequest`

实现能力：

- 从受控目录解析部门、用户标准化 JSON 文件
- 校验路径必须位于导入根目录下
- 校验仅支持 `.json`
- 校验文件存在且大小不超限

### 2.3 手工文件导入接口

新增接口：

- `POST /api/v1/departments/import/feishu-file`
- `POST /api/v1/users/import/feishu-file`

接口语义：

- 当前前端页面导入按钮应接入这两个文件导入接口
- 在线接口 `/api/v1/departments/sync/feishu` 与 `/api/v1/users/sync/feishu` 保留，但默认不作为前端入口

### 2.4 导入执行链路调整

当前飞书导入代码同时支持两种执行来源：

- 在线来源：飞书开放平台 API
- 文件来源：标准化 JSON 文档

共用能力：

- 同步批次 `SyncBatch`
- 同步任务 `SyncJob`
- 差异记录 `SyncDiff`
- 部门/用户导入服务
- MySQL 与 LDAP 双写逻辑

### 2.5 权限与数据库迁移

新增 Flyway 脚本：

- `V9__feishu_file_import_entry.sql`

新增权限点：

- `DEPT_FEISHU_FILE_IMPORT`
- `USER_FEISHU_FILE_IMPORT`

并已授予 `ADMIN`。

## 3. 测试验证

本次新增或补充的测试包括：

- `FeishuImportDocumentResolverTest`
- `FeishuDepartmentImportServiceTest`
- `FeishuUserImportServiceTest`
- `SyncApplicationServiceTest`
- `SyncScheduleLauncherTest`
- `PrototypeIntegrationTest`

执行命令：

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

测试结果：

- Tests run: `116`
- Failures: `0`
- Errors: `0`

## 4. 文档更新

已同步更新：

- `docs/project-design.md`

补充内容包括：

- 飞书导入模式调整为“当前仅开放手动文件导入”
- 在线导入与定时代码保留但默认停用的边界
- 新增文件导入接口清单
- 最新测试结果

## 5. 下一步建议

下一轮建议继续推进：

1. 前端用户管理页、分组管理页接入文件导入按钮
2. 文件导入结果摘要展示
3. GitLab 真实 LDAP 接入联调
