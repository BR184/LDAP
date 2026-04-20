# Jenkins LDAP 接入模板

## 1. 适用范围

- 仅认证
- 不托管授权
- Jenkins 权限继续由本地授权策略维护

## 2. 固定契约

- `login_attr`: `uid`
- `user_base`: `ou=people,<baseDn>`
- `user_filter`: `(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`

## 3. 字段映射

- LDAP Server：`ldap://<host>:<port>` 或 `ldaps://<host>:<port>`
- Root DN：`<baseDn>`
- User Search Base：`ou=people`
- User Search Filter：`(&(objectClass=inetOrgPerson)(uid={0})(employeeType=ENABLED))`
- Display Name Attribute：`cn`
- Mail Attribute：`mail`
- Manager DN：`<bind_dn>`
- Manager Password：通过外部安全配置注入

## 4. 产品说明

- Jenkins 常见问题是退化为只按 `uid={0}` 搜索，必须显式改为带状态过滤的表达式
- LDAP 插件缓存策略需要在联调阶段单独验证
- 首次登录后的角色和权限仍在 Jenkins 本地维护
