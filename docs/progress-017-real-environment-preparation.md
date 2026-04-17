# 项目进展记录 017 - 真环境配置与迁移准备

## 1. 里程碑说明

本次围绕第二期第一步“真环境配置与现有功能迁移”完成了基础代码与配置落地，使项目在保留本地原型调试体验的同时，具备切换到 `MySQL + OpenLDAP` 真实运行环境的准备能力。

完成时间：2026-04-17

## 2. 本次完成内容

### 2.1 Profile 配置拆分

已完成以下环境配置文件拆分：

- `application.yml`
- `application-local.yml`
- `application-dev.yml`
- `application-test.yml`
- `application-prod.yml`

当前约定如下：

- `local`：默认 profile，使用 `H2 + LDAP stub`
- `dev/test/prod`：使用 `MySQL + OpenLDAP`

### 2.2 真实环境底座补充

本次已补充：

- MySQL 驱动依赖
- `MySQL + OpenLDAP` 容器联调模板 `deploy/docker-compose-dev.yml`
- OpenLDAP 初始化 LDIF `deploy/openldap/bootstrap/01-base.ldif`

其中 LDAP 初始化模板已预置：

- `ou=people`
- `ou=groups`
- `uid=placeholder`

### 2.3 启动校验与健康检查

为保证真实环境切换时尽早暴露问题，本次新增：

- `EnvironmentStartupVerifier`
- `LdapDirectoryHealthIndicator`

实现能力包括：

- 数据库连通性校验
- LDAP `people-ou`、`groups-ou` 可访问性校验
- 占位用户存在性校验
- Actuator LDAP 健康检查

### 2.4 MySQL 兼容迁移脚本调整

本次已将 Flyway 初始化脚本调整为更适配 MySQL 的写法，主要包括：

- 自增主键改为 `AUTO_INCREMENT`
- 审计日志大字段改为 `TEXT`

这样可以同时兼容：

- 本地 `H2 MySQL mode`
- 真实 `MySQL`

### 2.5 真实 LDAP 适配收口

本次顺手修复了两个更容易在真实 OpenLDAP 环境暴露的问题：

- 用户 LDAP 属性写入改为“非空才写”
- 用户从所有 LDAP group 移除时，按 `businessCategory` 回查真实 group 编码

这两点在 stub 环境中不明显，但在真实 LDAP 中会直接影响运行稳定性。

## 3. 测试验证

本次改动后已执行全量测试，并全部通过。

执行命令：

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

测试结果：

- Tests run: `72`
- Failures: `0`
- Errors: `0`

## 4. 文档更新

已同步更新：

- `docs/project-design.md`

主要补充内容包括：

- profile 配置拆分说明
- 启动校验与 LDAP 健康检查
- 真环境准备项清单
- 最新测试结果

## 5. 下一步建议

下一轮建议按第二期开发清单继续推进：

1. 同步公共底座
2. 飞书部门导入
3. 飞书用户导入
4. MySQL 与 LDAP 对账补偿
