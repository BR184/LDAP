# 项目进展记录 018 - 配置文件注释补充

## 1. 里程碑说明

本次主要为环境配置文件补充说明性注释，帮助快速区分 `local / dev / test / prod` 四类运行环境的用途和关键差异。

完成时间：2026-04-17

## 2. 本次完成内容

已为以下文件补充注释：

- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-test.yml`
- `src/main/resources/application-prod.yml`

本次注释重点说明了：

- 公共配置与 profile 差异配置的边界
- 本地原型环境与真实环境的区别
- `H2 + LDAP stub` 与 `MySQL + OpenLDAP` 的差异
- Swagger、H2 console、启动校验在不同环境中的开关原因
- 生产环境为什么不提供默认敏感配置

## 3. 说明

本次仅补充注释，未修改业务逻辑，未执行测试。
