# 项目进展记录 002 - 单模块包分层重构

## 1. 里程碑说明

本次完成了工程结构调整：将原先按 `common/domain/application/infrastructure/interfaces/boot/test` 拆分的 Maven 多模块原型，重构为“单 Maven 工程 + 分层包结构”。

完成时间：2026-04-15

## 2. 本次改动内容

### 2.1 工程结构调整

已将代码统一收敛到根项目的标准目录：

- `src/main/java/com/company/idm/common`
- `src/main/java/com/company/idm/domain`
- `src/main/java/com/company/idm/application`
- `src/main/java/com/company/idm/interfaces`
- `src/main/java/com/company/idm/infrastructure`
- `src/main/java/com/company/idm/boot`
- `src/main/resources`
- `src/test/java`

### 2.2 Maven 配置调整

已将根 `pom.xml` 改为单模块 Spring Boot 工程：

- 根项目从 `packaging=pom` 调整为 `packaging=jar`
- 去除原先对子模块的 `modules` 声明
- 将原先各模块依赖合并到根 POM
- 保留 Spring Boot 插件、Lombok 注解处理、测试依赖

### 2.3 目录清理

已移除以下旧模块目录：

- `corp-idm-common`
- `corp-idm-domain`
- `corp-idm-application`
- `corp-idm-infrastructure`
- `corp-idm-interfaces`
- `corp-idm-boot`
- `corp-idm-test`

## 3. 调整原因

本次调整的目标是让当前原型更贴近实际开发体验：

- 原型阶段功能仍在快速演进，单模块更便于调试和理解
- 当前分层已经通过包结构体现，没有必要过早增加 Maven 模块维护成本
- 后续如果边界稳定，仍可再从包结构平滑拆回多模块

## 4. 当前结果

调整后，项目仍保留原先的逻辑分层设计：

- `domain`：核心实体与仓储接口
- `application`：应用服务与用例编排
- `interfaces`：控制器、请求响应对象、异常处理
- `infrastructure`：MyBatis-Plus、LDAP、JWT、Casbin、安全配置

也就是说，本次是“工程形态简化”，不是“架构分层退化”。

## 5. 验证结果

本次重构后已重新执行测试，结果通过。

执行命令：

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

验证点仍包括：

- 管理员登录
- 查询当前用户
- 创建用户
- 禁用用户
- Casbin 权限生效

## 6. 下一步建议

基于当前单模块包分层结构，下一轮可以继续在不增加工程复杂度的前提下扩展功能：

1. 补充用户详情、编辑、重置密码、角色分配
2. 补充部门管理与导入模型
3. 接入真实 OpenLDAP 联调
4. 增加飞书导入预处理能力
