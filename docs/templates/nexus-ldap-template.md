# Nexus LDAP 接入模板

## 1. 适用范围

- 仅认证
- 不托管授权
- Nexus 本地角色与权限继续在系统内维护

## 2. 固定契约

- `login_attr`: `uid`
- `user_base`: `ou=people,<baseDn>`
- `user_filter`: `(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`

## 3. 字段映射

- Connection URL：`ldap://<host>:<port>` 或 `ldaps://<host>:<port>`
- Search Base：`<baseDn>`
- Authentication Method：Simple
- Bind DN：`<bind_dn>`
- Bind Password：通过外部安全配置注入
- User Base DN：`ou=people,<baseDn>`
- User Filter：`(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`
- User ID Attribute：`uid`
- Real Name Attribute：`cn`
- Email Attribute：`mail`

## 4. 产品说明

- Nexus 需要单独确认 LDAP Realm 的启用顺序
- 缓存和同步延迟要纳入联调验收
- LDAP 认证成功后，仓库与角色权限仍由 Nexus 本地角色模型决定
