# LDAP 通用目录契约

## 1. 适用范围

本契约适用于 GitLab、Jenkins、Nexus、禅道等内网第三方系统接入当前平台维护的 OpenLDAP。

## 2. 固定规则

- `login_attr`: `uid`
- `user_base`: `ou=people,<baseDn>`
- `group_base`: `ou=groups,<baseDn>`
- `user_filter`: `(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`

## 3. 必需属性

- `uid`
- `cn`
- `sn`
- `mail`
- `employeeType`

## 4. 推荐扩展属性

- `mobile`
- `employeeNumber`
- `departmentNumber`

## 5. 启停语义

- `employeeType=ENABLED`：允许第三方系统登录
- `employeeType=DISABLED`：必须被第三方系统过滤器拦截

## 6. 安全规则

- Bind 账号必须为只读账号
- Bind 密码不得写入仓库
- 文档、截图和联调记录不得保存明文密码
- 禁用用户必须通过过滤器被拦截，不能仅依赖人工约定

## 7. 不允许的变体

- 不允许改成邮箱作为登录字段
- 不允许改成工号作为登录字段
- 不允许改成手机号作为登录字段
- 不允许去掉 `employeeType=ENABLED` 过滤条件
