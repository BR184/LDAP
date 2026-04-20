# 禅道 LDAP 接入模板

## 1. 适用范围

- 仅认证
- 不托管授权
- 禅道业务权限继续由禅道本地模型维护

## 2. 固定契约

- `login_attr`: `uid`
- `user_base`: `ou=people,<baseDn>`
- `user_filter`: `(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`

## 3. 字段映射建议

- LDAP Server：`<host>:<port>`
- Base DN：`<baseDn>`
- Bind DN：`<bind_dn>`
- Bind Password：通过外部安全配置注入
- User Search Base：`ou=people,<baseDn>`
- User Search Filter：`(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`
- Username Attribute：`uid`
- Display Name Attribute：`cn`
- Mail Attribute：`mail`

## 4. 产品说明

> 待目标版本官方手册确认最终字段名，本模板先作为联调占位稿。

- 不允许脱离统一目录契约单独定义登录字段
- 不允许跳过禁用用户登录拦截验证
- 目标版本封板前，需补齐正式字段说明和截图校验
