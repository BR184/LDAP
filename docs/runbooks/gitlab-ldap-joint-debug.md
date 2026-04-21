# GitLab 真实联调操作单

## 1. 目标与边界

本操作单用于指导 GitLab 接入当前平台维护的 OpenLDAP，并完成首次真实联调。

本次联调边界如下：
- GitLab 只使用 LDAP 做认证
- GitLab 项目、组、角色权限继续由 GitLab 本地维护
- 平台不进入 GitLab 运行时认证链路
- 登录字段固定为 `uid`
- GitLab 生效后的等效过滤器固定为 `(&(uid=%{username})(&(objectClass=inetOrgPerson)(employeeType=ENABLED)))`

说明：
- GitLab 后台字段名可能因版本略有差异，但语义应严格对齐本操作单和模板文档
- 首次联调建议使用测试环境 GitLab，不建议直接在生产 GitLab 首次试错

## 2. 参考资料

- 通用目录契约：[ldap-common-contract.md](/C:/Users/Administrator/ldap/docs/templates/ldap-common-contract.md)
- GitLab 模板：[gitlab-ldap-template.md](/C:/Users/Administrator/ldap/docs/templates/gitlab-ldap-template.md)
- 接入前预检清单：[ldap-precheck.md](/C:/Users/Administrator/ldap/docs/checklists/ldap-precheck.md)
- 接入后验收清单：[ldap-acceptance.md](/C:/Users/Administrator/ldap/docs/checklists/ldap-acceptance.md)
- 失败回滚手册：[third-party-ldap-rollback.md](/C:/Users/Administrator/ldap/docs/runbooks/third-party-ldap-rollback.md)

## 3. 前置准备

### 3.1 环境准备

- [ ] 准备一套可回滚的 GitLab 测试环境
- [ ] 确认 GitLab 服务器可访问 LDAP `host:port`
- [ ] 确认平台已切换到可用 LDAP 环境，推荐 `app.ldap.mode=spring`
- [ ] 保留一个 GitLab 本地管理员账号，且确认密码可用
- [ ] 准备联调时间窗口，避免在业务高峰首次修改认证配置

### 3.2 账号准备

- [ ] 准备一个启用用户样本 `enabled_user`
- [ ] 准备一个禁用用户样本 `disabled_user`
- [ ] 准备一个不存在的用户名 `not_exists_user`
- [ ] 确认启用用户、禁用用户都位于 `ou=people,<baseDn>` 下
- [ ] 确认启用用户具备 `uid`、`cn`、`mail`、`employeeType` 属性

### 3.3 安全准备

- [ ] 准备只读 Bind 账号
- [ ] 不使用 LDAP 管理员账号作为 Bind 账号
- [ ] Bind 密码仅通过环境变量或安全配置平台注入
- [ ] 最终配置快照必须脱敏，不得保留明文密码

## 4. 平台侧预检 √

### 4.1 获取统一契约

调用：
- `GET /api/v1/ldap/framework`
- `GET /api/v1/ldap/templates/gitlab`

重点确认以下值：
- `loginAttr = uid`
- `userBase = ou=people,<baseDn>`
- `userFilter = (&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`
- `authorizationMode = LOCAL_ONLY`

建议保存：
- 平台接口返回的脱敏 JSON
- 当前使用的 `baseDn`
- 当前使用的 `host`、`port`

### 4.2 执行联调预检

调用：
- `POST /api/v1/ldap/precheck`

请求示例：
```json
{
  "systemCode": "gitlab",
  "enabledUsername": "zhangsan",
  "disabledUsername": "lisi"
}
```

预检通过标准：
- `LOGIN_ATTR_FIXED = PASS`
- `PEOPLE_OU_ACCESS = PASS`
- `GROUPS_OU_ACCESS = PASS`
- `ENABLED_USER_UNIQUE = PASS`
- `ENABLED_USER_FILTER_MATCH = PASS`
- `DISABLED_USER_FILTER_BLOCK = PASS`
- `overallStatus = PASS`

未通过时处理原则：
- 不进入 GitLab 配置阶段
- 先修复 LDAP 目录、用户状态、网络或过滤器问题
- 修复后重新执行预检

## 5. GitLab 配置录入

### 5.1 配置来源

GitLab 配置以平台模板接口和模板文档为准，不手工发挥。

推荐对照：
- [gitlab-ldap-template.md](/C:/Users/Administrator/ldap/docs/templates/gitlab-ldap-template.md)

### 5.2 关键字段对照

| 配置项语义 | 目标值 |
| --- | --- |
| Host | 平台模板返回的 `host` |
| Port | 平台模板返回的 `port` |
| Base DN | 平台模板返回的 `base_dn` |
| Bind DN | 平台模板返回的 `bind_dn` |
| Bind Password | 安全注入，不写入文档 |
| User Base | 平台模板返回的 `user_base` |
| User Filter | `(&(objectClass=inetOrgPerson)(employeeType=ENABLED))` |
| Login Attribute | `uid` |
| Name Attribute | `cn` |
| Email Attribute | `mail` |
| Encryption | `plain` / `start_tls` / `simple_tls`，以当前环境实际能力为准 |

### 5.3 配置录入注意事项

- [ ] 不允许将登录字段改为邮箱、工号或手机号
- [ ] 不允许去掉 `employeeType=ENABLED`
- [ ] 不允许把 `userBase` 改到 `ou=people,<baseDn>` 之外
- [ ] GitLab 后台 `user_filter` 不重复填写 `uid={login}`，由 GitLab 自动拼接 `uid=%{username}`
- [ ] 如 GitLab 支持“首登自动创建用户”，可以开启，但需记录该行为
- [ ] GitLab 本地管理员账号不要删，保留为回滚后入口

## 6. 联调执行步骤

### 步骤 1：保存修改前基线

- [ ] 截图或导出 GitLab 当前认证配置，注意脱敏
- [ ] 记录当前本地管理员登录是否正常
- [ ] 记录当前联调负责人、时间、环境

### 步骤 2：录入 GitLab LDAP 配置

- [ ] 按模板逐项填入 GitLab 后台
- [ ] 再次核对 `Login Attribute` 与 `User Filter`
- [ ] 保存配置，但先不通知业务用户使用

### 步骤 3：执行连接验证

- [ ] 如 GitLab 提供 LDAP 测试按钮，先执行连接测试
- [ ] 确认 Bind 账号可连接并能搜索到 `ou=people`
- [ ] 若连接失败，优先检查网络、端口、TLS 和 Bind 账号

### 步骤 4：执行登录测试

按以下顺序执行：

1. 启用用户 + 正确密码
   - 预期：登录成功
2. 禁用用户 + 正确密码
   - 预期：登录失败
3. 启用用户 + 错误密码
   - 预期：登录失败
4. 不存在用户
   - 预期：登录失败
5. 首次登录用户
   - 预期：行为符合预期，记录是否自动创建本地用户

### 步骤 5：执行缓存验证

- [ ] 先用启用用户成功登录一次
- [ ] 在平台或 LDAP 中将该用户改为禁用
- [ ] 记录 GitLab 多久开始拒绝该用户登录
- [ ] 将缓存结论写入联调记录

## 7. 验收标准

联调完成后，至少满足以下条件：
- [ ] 启用用户可以正常登录 GitLab
- [ ] 禁用用户无法登录 GitLab
- [ ] 错误密码无法登录
- [ ] 不存在用户无法登录
- [ ] 首登行为已确认
- [ ] 缓存行为已记录
- [ ] 最终生效配置已做脱敏归档
- [ ] GitLab 本地权限边界已确认

## 8. 联调记录模板

建议记录以下信息：

- 环境：`test` / `staging` / `prod`
- GitLab 地址：
- LDAP Host：
- LDAP Port：
- Base DN：
- User Base：
- User Filter：
- Encryption：
- Bind 账号：
- 启用用户样本：
- 禁用用户样本：
- 首登行为：
- 缓存行为：
- 最终结论：通过 / 不通过
- 责任人：
- 联调时间：

## 9. 失败判定与回滚

出现以下任一情况，建议立即回滚：
- 大面积用户无法登录
- Bind 账号异常
- TLS 配置错误导致无法建立连接
- 启用用户无法命中统一过滤器
- 禁用用户仍然能登录

回滚执行：
- 按 [third-party-ldap-rollback.md](/C:/Users/Administrator/ldap/docs/runbooks/third-party-ldap-rollback.md) 操作

最小回滚步骤：
1. 关闭 GitLab LDAP 认证入口
2. 恢复 GitLab 原本地认证配置
3. 使用本地管理员账号验证可登录
4. 记录故障范围、时间和责任人

## 10. 建议的实际执行顺序

1. 平台侧调用 `/api/v1/ldap/framework`
2. 平台侧调用 `/api/v1/ldap/templates/gitlab`
3. 平台侧调用 `/api/v1/ldap/precheck`
4. GitLab 后台录入 LDAP 配置
5. 执行连接测试
6. 执行 5 组登录测试
7. 执行缓存验证
8. 记录验收结果并留存脱敏快照
9. 通过后再通知业务侧试用
