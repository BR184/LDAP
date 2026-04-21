# 项目进展记录 022 - 飞书开放平台直连同步

## 1. 里程碑说明

本次将飞书导入方式从“JSON / 文件导入”调整为“后端直接调用飞书开放平台 API 拉取数据”，并同步完成了部门、用户导入代码重构与接口入口调整。

完成时间：2026-04-17

## 2. 本次完成内容

### 2.1 飞书导入方式调整

本次已删除旧导入方式的主流程支持：

- 前端 `payloadJson` 直传导入
- 后端读取本地导入文件导入

当前统一调整为：

- 手动导入：前端按钮触发，后端直接调用飞书开放平台 API 拉数据
- 自动导入：定时任务触发，后端直接调用飞书开放平台 API 拉数据

### 2.2 飞书开放平台接入能力补充

本次新增：

- `FeishuOpenApiProperties`
- `FeishuAccessTokenService`
- `DefaultFeishuAccessTokenService`
- `FeishuOpenApiClient`
- `FeishuDepartmentRemoteService`
- `FeishuUserRemoteService`

当前已支持：

- 获取 `tenant_access_token`
- 拉取飞书部门数据
- 拉取飞书用户数据

### 2.3 手动入口调整

不再单独提供飞书同步页面，当前改为：

- `POST /api/v1/departments/sync/feishu`
- `POST /api/v1/users/sync/feishu`

页面约定为：

- 分组管理页放“同步飞书分组信息”按钮
- 用户管理页放“同步飞书用户信息”按钮

### 2.4 导入执行规则补充

当前实现中：

- 部门同步：只执行飞书部门导入
- 用户同步：先执行飞书部门导入，再执行飞书用户导入

这样可以保证用户主部门映射所依赖的部门数据完整存在。

## 3. 测试验证

本次改动后已执行全量测试，并全部通过。

执行命令：

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

测试结果：

- Tests run: `88`
- Failures: `0`
- Errors: `0`

## 4. 文档更新

已同步更新：

- `docs/project-design.md`

主要修订内容包括：

- 飞书导入逻辑流程
- 手动/自动导入模式
- 飞书部门导入设计
- 飞书用户导入设计
- 同步接口清单
- 第二期开发清单中的飞书导入描述

## 5. 下一步建议

下一轮建议继续推进：

1. 更细粒度的 MySQL 与 LDAP 对账补偿
2. 飞书 API 失败重试与限流处理
3. 用户管理页、分组管理页前端同步按钮接入
4. GitLab 真实 LDAP 接入联调
