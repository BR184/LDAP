# 项目进展记录 001 - 最小原型

## 1. 里程碑说明

本次完成基于《企业统一身份与LDAP管理平台项目设计文档》的第一版最小可运行原型，目标是先打通一期核心链路：

- 平台登录认证
- 用户管理
- 角色权限管理
- LDAP 适配接口
- JWT + Casbin 鉴权
- 多模块工程骨架

完成时间：2026-04-15

## 2. 本次已实现内容

### 2.1 工程结构

已按设计文档建立 Maven 多模块结构：

- `corp-idm-common`
- `corp-idm-domain`
- `corp-idm-application`
- `corp-idm-infrastructure`
- `corp-idm-interfaces`
- `corp-idm-boot`
- `corp-idm-test`

### 2.2 一期最小功能切片

已实现以下最小 API 原型：

- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `GET /api/v1/users`
- `POST /api/v1/users`
- `PUT /api/v1/users/{id}/status`
- `GET /api/v1/roles`
- `POST /api/v1/roles`
- `PUT /api/v1/roles/{id}/permissions`
- `GET /api/v1/permissions/tree`

### 2.3 已接入技术栈

- Spring Boot
- Spring LDAP
- Spring Security
- JWT
- Casbin
- Lombok
- MyBatis-Plus
- Flyway
- H2
- JUnit 5
- Spring Boot Test

### 2.4 核心原型实现点

- 已建立 `User`、`Department`、`Role`、`Permission`、`AuditLog` 等核心实体模型
- 已建立 MyBatis-Plus DO、Mapper、Repository 实现
- 已建立 JWT 令牌生成、解析与过滤器
- 已建立基于 Casbin 的 RBAC 权限校验与策略刷新
- 已建立 LDAP 目录服务抽象 `LdapDirectoryService`
- 已同时提供两种 LDAP 实现：
  - `StubLdapDirectoryService`：原型默认启用，便于本地联调
  - `SpringLdapDirectoryService`：为接入真实 OpenLDAP 预留
- 已通过 Flyway 初始化原型数据库结构和种子数据

## 3. 原型默认数据

初始化数据如下：

- 部门：`D001 / 研发中心`
- 角色：`SUPER_ADMIN`
- 管理员账号：`admin`
- 原型 LDAP 密码：`admin123456`

说明：

- 当前原型默认在 `application.yml` 中使用 H2 内存库
- 当前原型默认使用 `app.ldap.mode=stub`
- 切换真实 OpenLDAP 时，可继续沿用同一套 `LdapDirectoryService` 抽象

## 4. 测试与验证

本次已执行自动化测试，并通过：

- 管理员登录并读取当前用户信息
- 管理员创建用户
- 管理员禁用用户
- Casbin 对用户、角色接口的授权生效

执行命令：

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

说明：

- 由于环境中原先没有 Maven，本次在工作区下下载了本地 Maven 运行时到 `.tools/apache-maven-3.9.6`

## 5. 当前已知约束

- 真实 OpenLDAP 联调尚未开始，当前默认走 stub 模式
- 用户密码未接入真实 LDAP 密码策略和加密方案
- 权限点当前以种子数据初始化，尚未提供权限点维护接口
- 部门当前作为基础实体和表结构已落地，但尚未提供组织架构管理界面和同步流程
- 消息管理、流程中心、调度引擎、插件管理、API 网关仍为后续迭代范围

## 6. 下一步建议

下一轮建议优先扩展以下内容之一：

1. 完善用户管理，增加详情、编辑、重置密码、角色分配接口
2. 完善角色权限管理，增加权限点维护接口
3. 接入真实 OpenLDAP 连接配置与联调
4. 增加部门管理与用户导入模型，为飞书同步做准备
