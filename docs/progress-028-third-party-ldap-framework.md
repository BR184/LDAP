# 项目进展记录 028 - 第三方 LDAP 通用接入框架

## 1. 里程碑说明
本次围绕“第三方系统 LDAP 通用接入框架”完成了从设计文档到后端能力的正式落地，不再只停留在模板说明层，而是补齐了统一契约输出、系统模板生成、联调预检和权限接入能力。
本次实现遵循当前项目“平台维护 LDAP 数据，第三方系统运行时直接连接 OpenLDAP，平台不进入运行时认证链路”的既定边界，复杂度控制在当前阶段可直接落地、可直接联调用的中间方案。

完成时间：2026-04-21

## 2. 本次完成内容

### 2.1 第三方 LDAP 通用接入应用服务

新增：
- `ThirdPartyLdapIntegrationApplicationService`
- `ThirdPartyLdapFrameworkDetail`
- `ThirdPartyLdapTemplateDetail`
- `ThirdPartyLdapPrecheckReport`
- `ThirdPartyLdapSystemType`

落地能力：
- 统一输出第三方系统接入 OpenLDAP 所需的标准目录契约
- 固定登录字段为 `uid`
- 固定标准过滤器为 `(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`
- 固定授权边界为“LDAP 只负责认证，第三方系统本地继续负责授权”

### 2.2 第三方系统模板生成接口

新增接口：
- `GET /api/v1/ldap/framework`
- `GET /api/v1/ldap/templates/{systemCode}`

当前支持的模板类型：
- `gitlab`
- `jenkins`
- `nexus`
- `zentao`

模板生成策略：
- 基于当前 `app.ldap.*` 配置自动生成连接参数
- 自动推导 `host`、`port`、`baseDn`、`userBase`
- 统一输出各系统对应的用户过滤器格式
- `bind_password` 只返回占位符 `${LDAP_BIND_PASSWORD}`，不暴露真实密钥

### 2.3 联调预检接口

新增接口：
- `POST /api/v1/ldap/precheck`

预检能力：
- 校验统一登录字段规则
- 校验启用用户是否能被统一过滤器命中
- 校验禁用用户是否会被统一过滤器拦截
- 在 `spring` 模式下补充真实 LDAP 目录可访问性检查
- 在 `stub` 模式下复用现有 `LdapDirectoryService` 内存目录完成本地预检演练

### 2.4 权限与数据库迁移

新增 Flyway 脚本：
- `V10__third_party_ldap_framework.sql`

新增权限点：
- `LDAP_FRAMEWORK_READ`
- `LDAP_TEMPLATE_READ`
- `LDAP_PRECHECK_EXECUTE`

并已授予 `ADMIN`。

## 3. 测试验证

本次新增或补充的测试包括：
- `ThirdPartyLdapIntegrationApplicationServiceTest`
- `PrototypeIntegrationTest`

覆盖重点包括：
- 标准契约输出是否正确
- GitLab 模板是否正确生成且不泄露真实 bind 密码
- `stub` 模式预检是否能够区分启用用户与禁用用户
- 框架查询、模板查询、预检接口是否完成权限接线并可正常访问

执行命令：
```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

测试结果：
- Tests run: `124`
- Failures: `0`
- Errors: `0`

## 4. 文档更新

已同步更新：
- `docs/project-design.md`

补充内容包括：
- 第三方 LDAP 通用接入框架的代码落地说明
- 已提供的运行时接口清单
- 模板生成与预检能力边界
- 权限迁移与管理员开放范围

## 5. 下一步建议
下一轮建议继续推进：

1. 以 GitLab 作为首个真实接入样板，基于模板接口和预检接口完成现场联调
2. 将预检结果、模板快照和验收结论沉淀为标准联调记录单
3. 在前端补充第三方 LDAP 接入管理页或联调辅助页，降低运维接入成本
