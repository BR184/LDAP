# GitLab LDAP 接入模板

## 1. 接入范围

- 只使用 LDAP 做认证
- GitLab 本地权限继续由 GitLab 自身维护
- 允许首登自动创建本地用户

## 2. 变量

- `host`
- `port`
- `bind_dn`
- `bind_password`
- `base_dn`
- `user_base`
- `user_filter`
- `encryption`

## 3. 固定映射

- `uid` 字段：`uid`
- `name` 字段：`cn`
- `email` 字段：`mail`

## 4. 固定规则

- 登录字段必须为 `uid`
- 用户搜索根必须为 `ou=people,<baseDn>`
- 统一过滤器必须为 `(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`

## 5. 模板要点

- Host：`<host>`
- Port：`<port>`
- Base：`<base_dn>`
- Bind DN：`<bind_dn>`
- Password：通过外部安全配置注入
- User filter：`(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`
- Encryption：按环境选择 `plain`、`start_tls` 或 `simple_tls`

## 6. 说明

- GitLab 可作为首个标准接入样板
- LDAP 认证成功不代表用户自动拥有项目权限
- 上线时需要记录最终生效配置的脱敏快照
