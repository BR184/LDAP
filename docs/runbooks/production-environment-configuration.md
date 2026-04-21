# 生产环境配置说明

## 1. 适用范围

本文档用于说明当前项目后端在生产环境下的正式配置方式，重点覆盖：

- 生产环境启用方式
- 必填环境变量
- 敏感配置注入方式
- 启动前校验要求
- 不同部署方式下的配置建议

本文档仅面向后端运行环境，不包含前端页面配置。

## 2. 基本原则

生产环境配置遵循以下原则：

- 配置外置：环境相关配置不写死在仓库代码中
- 敏感信息不入库：密码、密钥、令牌不得提交到 Git 仓库
- 缺失即失败：生产环境不提供敏感配置默认值，缺失时应直接启动失败
- 最小暴露面：生产环境默认关闭 Swagger
- 启动即校验：数据库与 LDAP 基础连通性在启动阶段即完成校验

对应配置入口可参考：

- [application.yml](/C:/Users/Administrator/ldap/src/main/resources/application.yml)
- [application-prod.yml](/C:/Users/Administrator/ldap/src/main/resources/application-prod.yml)

## 3. 生产环境启用方式

生产环境必须显式启用 `prod` profile。

推荐启动方式：

```powershell
java -jar corp-idm-platform.jar --spring.profiles.active=prod
```

或：

```powershell
$env:SPRING_PROFILES_ACTIVE='prod'
java -jar corp-idm-platform.jar
```

不要依赖默认 profile。默认 profile 主要面向本地原型调试。

## 4. 必填环境变量

### 4.1 数据库配置

以下变量在生产环境必填：

- `APP_DB_URL`
- `APP_DB_USERNAME`
- `APP_DB_PASSWORD`

说明：

- `APP_DB_URL` 应指向生产 MySQL
- `APP_DB_USERNAME` 与 `APP_DB_PASSWORD` 应为专用数据库账号
- 不建议复用高权限 root 账号

可选调优变量：

- `APP_DB_MAX_POOL_SIZE`
- `APP_DB_MIN_IDLE`
- `APP_DB_CONNECTION_TIMEOUT_MS`
- `APP_DB_VALIDATION_TIMEOUT_MS`

### 4.2 LDAP 配置

以下变量在生产环境必填：

- `APP_LDAP_URL`
- `APP_LDAP_BIND_DN`
- `APP_LDAP_BIND_PASSWORD`

以下变量如与默认目录契约不一致，也必须显式配置：

- `APP_LDAP_BASE_DN`
- `APP_LDAP_PEOPLE_OU`
- `APP_LDAP_GROUPS_OU`

说明：

- 生产环境固定使用真实 LDAP，即 `app.ldap.mode=spring`
- `APP_LDAP_BIND_DN` 建议使用只读 bind 账号
- 不建议使用 LDAP 管理员账号作为业务联调用 bind 账号

### 4.3 JWT 配置

以下变量在生产环境强烈建议显式配置：

- `APP_JWT_SECRET`

可选变量：

- `APP_JWT_EXPIRE_MINUTES`

说明：

- 生产环境不得使用 `application.yml` 中的原型默认 secret
- JWT secret 应满足足够长度、随机性和轮换要求

### 4.4 启动校验配置

以下变量建议按生产实际情况确认：

- `APP_STARTUP_CHECK_ENABLED`
- `APP_STARTUP_CHECK_VERIFY_PLACEHOLDER_USER`
- `APP_STARTUP_CHECK_PLACEHOLDER_UID`
- `APP_STARTUP_CHECK_DB_VALIDATION_TIMEOUT_SECONDS`

说明：

- 当前生产 profile 默认启用启动校验
- 默认会检查数据库连通性、LDAP 目录可访问性和占位用户存在性

### 4.5 飞书同步配置

如果生产环境需要启用飞书同步，还需配置：

- `APP_SYNC_FEISHU_ENABLED`
- `APP_SYNC_FEISHU_BASE_URL`
- `APP_SYNC_FEISHU_APP_ID`
- `APP_SYNC_FEISHU_APP_SECRET`
- `APP_SYNC_FEISHU_PAGE_SIZE`
- `APP_SYNC_FEISHU_CONNECT_TIMEOUT_MS`
- `APP_SYNC_FEISHU_READ_TIMEOUT_MS`
- `APP_SYNC_FEISHU_FILE_IMPORT_ENABLED`
- `APP_SYNC_FEISHU_FILE_IMPORT_ROOT_DIR`
- `APP_SYNC_FEISHU_FILE_IMPORT_MAX_SIZE`

若使用定时同步，还需配置：

- `APP_SYNC_SCHEDULE_FEISHU_ENABLED`
- `APP_SYNC_SCHEDULE_FEISHU_CRON`
- `APP_SYNC_SCHEDULE_RECONCILE_ENABLED`
- `APP_SYNC_SCHEDULE_RECONCILE_CRON`

当前代码中部分调度项仍主要通过配置文件固定，若生产启用前要进一步外置，建议另做一轮配置外置化收口。

## 5. 敏感配置分级

### 5.1 严格敏感，必须通过安全注入

以下配置不得写入仓库、文档正文、截图或普通配置文件：

- `APP_DB_PASSWORD`
- `APP_LDAP_BIND_PASSWORD`
- `APP_JWT_SECRET`
- `APP_SYNC_FEISHU_APP_SECRET`

### 5.2 中度敏感，建议通过安全平台统一注入

以下配置虽然不是密码，但仍建议视为敏感运行参数：

- `APP_DB_USERNAME`
- `APP_LDAP_BIND_DN`
- `APP_SYNC_FEISHU_APP_ID`

### 5.3 非敏感但环境相关

以下配置可以记录在运维文档中，但仍应按环境区分：

- `APP_DB_URL`
- `APP_LDAP_URL`
- `APP_LDAP_BASE_DN`
- `APP_LDAP_PEOPLE_OU`
- `APP_LDAP_GROUPS_OU`
- 连接池与超时参数

## 6. 推荐的敏感配置注入方式

### 6.1 推荐方式一：部署平台 Secret / 密钥管理系统

优先推荐：

- Kubernetes Secret
- 云厂商密钥管理服务
- 公司内部配置中心或密钥平台
- 容器编排平台的 Secret 注入能力

要求：

- 由平台在运行时注入到环境变量
- 密钥轮换可控
- 有访问审计
- 权限最小化

### 6.2 推荐方式二：受控的环境变量文件

如果当前部署方式尚未接入统一 Secret 平台，可退而求其次使用受控环境文件，例如：

```powershell
APP_DB_URL=jdbc:mysql://10.0.0.10:3306/corp_idm?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
APP_DB_USERNAME=corp_idm
APP_DB_PASSWORD=****** 
APP_LDAP_URL=ldap://10.0.0.20:389
APP_LDAP_BIND_DN=cn=readonly,dc=corp,dc=local
APP_LDAP_BIND_PASSWORD=******
APP_JWT_SECRET=******
```

要求：

- 文件不进入 Git
- 文件权限最小化
- 仅部署账号和运维账号可读
- 不通过 IM、邮件、工单正文传播明文

### 6.3 不推荐方式

以下方式禁止用于生产：

- 把密码直接写进 `application-prod.yml`
- 把密码直接提交到仓库
- 把密钥写进 runbook 正文
- 在截图中保留明文密码
- 在启动命令行参数中直接暴露明文密钥

## 7. 生产环境配置示例

以下示例仅展示结构，不展示真实敏感值：

```powershell
$env:SPRING_PROFILES_ACTIVE='prod'
$env:APP_DB_URL='jdbc:mysql://10.0.0.10:3306/corp_idm?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false'
$env:APP_DB_USERNAME='corp_idm'
$env:APP_DB_PASSWORD='***'
$env:APP_LDAP_URL='ldap://10.0.0.20:389'
$env:APP_LDAP_BIND_DN='cn=readonly,dc=corp,dc=local'
$env:APP_LDAP_BIND_PASSWORD='***'
$env:APP_LDAP_BASE_DN='dc=corp,dc=local'
$env:APP_LDAP_PEOPLE_OU='ou=people'
$env:APP_LDAP_GROUPS_OU='ou=groups'
$env:APP_JWT_SECRET='***'
java -jar corp-idm-platform.jar
```

## 8. 启动前检查项

生产启动前建议确认：

- [ ] 已显式启用 `prod` profile
- [ ] 数据库连接信息已注入
- [ ] LDAP 连接信息已注入
- [ ] JWT secret 已注入
- [ ] 敏感值未写入仓库
- [ ] 生产环境 Swagger 保持关闭
- [ ] 启动校验参数已确认
- [ ] LDAP 中占位用户存在
- [ ] bind 账号具备最小必要权限

## 9. 启动后验证项

应用启动成功后建议确认：

- [ ] 应用启动日志中无数据库校验失败
- [ ] 应用启动日志中无 LDAP 校验失败
- [ ] `/actuator/health` 返回正常
- [ ] 认证接口可正常工作
- [ ] 关键后端接口可正常访问
- [ ] 日志中未输出敏感参数明文

## 10. 与当前代码配置的对应关系

生产配置的直接代码来源如下：

- 数据库强制外置：
  - [application-prod.yml](/C:/Users/Administrator/ldap/src/main/resources/application-prod.yml)
- LDAP 强制外置：
  - [application-prod.yml](/C:/Users/Administrator/ldap/src/main/resources/application-prod.yml)
  - [AppLdapProperties.java](/C:/Users/Administrator/ldap/src/main/java/com/company/idm/infrastructure/config/AppLdapProperties.java)
- JWT 默认支持环境变量注入：
  - [application.yml](/C:/Users/Administrator/ldap/src/main/resources/application.yml)
  - [JwtProperties.java](/C:/Users/Administrator/ldap/src/main/java/com/company/idm/infrastructure/config/JwtProperties.java)
- 启动校验配置：
  - [application.yml](/C:/Users/Administrator/ldap/src/main/resources/application.yml)
  - [application-prod.yml](/C:/Users/Administrator/ldap/src/main/resources/application-prod.yml)
  - [StartupCheckProperties.java](/C:/Users/Administrator/ldap/src/main/java/com/company/idm/infrastructure/config/StartupCheckProperties.java)

## 11. 建议结论

当前项目已经具备生产环境配置外置的基础能力，但正式交付时应继续坚持：

- 所有敏感配置只通过受控环境变量或密钥平台注入
- 生产 profile 必须显式开启
- 生产配置说明、部署说明、回滚说明应配套维护
- 每次上线前按本文档做一次配置核对
