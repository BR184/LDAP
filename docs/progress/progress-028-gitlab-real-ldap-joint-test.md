# 项目进展记录 028 - GitLab 真实 LDAP 联调与收口

## 1. 里程碑说明

本次围绕“GitLab 接入当前平台维护的 OpenLDAP”完成了一轮真实联调，不再停留在模板输出和预检接口层，而是把 GitLab 测试容器、平台真实 `dev` 环境、OpenLDAP 目录和项目系统用户数据一起串起来验证。

本轮联调先完成了平台侧真实预检、GitLab 侧 LDAP 接入验证、启用/禁用用户登录验证、首登自动建号验证和缓存影响验证；随后根据真实联调暴露的问题，修正了 GitLab 模板的 `user_filter` 输出逻辑，并对联调过程中创建的测试账号做了项目系统、LDAP、GitLab 三侧清理。

完成时间：2026-04-21

## 2. 本次完成内容

### 2.1 GitLab 真实联调链路打通

本次使用本机现有测试容器完成了真实联调：

- GitLab：`corp-idm-gitlab-test`
- OpenLDAP：`corp-idm-realtest-openldap`
- MySQL：`corp-idm-realtest-mysql`
- 平台运行方式：`Spring Boot dev profile`

真实联调覆盖的关键步骤包括：

- 平台启动校验通过，确认数据库与 LDAP 目录连通
- 平台 `/api/v1/ldap/framework`、`/api/v1/ldap/templates/gitlab`、`/api/v1/ldap/precheck` 在真实 `spring` 模式下可用
- GitLab 容器内 `gitlab-rake gitlab:ldap:check` 成功
- GitLab Web 登录页真实提交 LDAP 登录表单
- 启用样本用户可登录 GitLab
- 禁用样本用户不可登录 GitLab
- 错误密码不可登录
- 不存在用户不可登录
- 本地 `root` 标准登录仍可用，保留回滚入口

### 2.2 发现并修正 GitLab 专属过滤器问题

联调初期发现一个真实兼容性问题：

- 平台原先给 GitLab 模板输出的 `user_filter` 为 `(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`
- GitLab 自身会再按 `uid=%{username}` 拼接一次登录过滤条件
- 两者叠加后，GitLab 最终使用的过滤器变成了重复嵌套结构，导致真实 Web 登录出现 `invalid_credentials`

本次已按 GitLab 真实行为修正：

- 平台统一目录契约 `framework.userFilter` 继续保留标准表达，作为平台内部统一预检口径
- GitLab 模板输出改为仅保留附加约束：`(&(objectClass=inetOrgPerson)(employeeType=ENABLED))`
- 说明文档明确补充：GitLab 会自动追加 `uid=%{username}`，后台 `user_filter` 不应重复填写 `uid={login}`

对应代码与测试已同步更新：

- `src/main/java/com/company/idm/application/ldap/ThirdPartyLdapSystemType.java`
- `src/main/java/com/company/idm/application/ldap/ThirdPartyLdapIntegrationApplicationService.java`
- `src/test/java/com/company/idm/test/ThirdPartyLdapIntegrationApplicationServiceTest.java`
- `src/test/java/com/company/idm/test/PrototypeIntegrationTest.java`

### 2.3 GitLab 首登自动建号验证通过

本次使用启用样本账号真实登录 GitLab 后，已验证：

- GitLab 能按 LDAP 身份自动创建本地用户
- 自动创建用户的 `provider` 为 `ldapmain`
- `extern_uid` 与 LDAP 用户 DN 正常绑定
- 禁用样本用户不会被自动创建为 GitLab 本地用户

这说明当前方案已经满足“LDAP 只负责认证，GitLab 本地继续维护权限和本地账号映射”的既定边界。

### 2.4 联调测试账号清理完成

本轮真实联调先后创建了以下测试账号：

- `gitlabe1776737403`
- `gitlabd1776737403`
- `gitlabe1776737867`
- `gitlabd1776737867`

清理动作已完成：

- 项目系统 `sys_user` 中 4 条测试用户记录均已逻辑删除
- OpenLDAP 中对应 4 条 `uid=gitlab*` 用户目录项已删除
- GitLab 本地自动建号产生的 `gitlab*` 测试用户已无残留

清理过程中还顺手修复了一次目录边界问题：

- 删除最后一个挂在 `D001` 分组下的测试账号时，LDAP `groupOfNames` 因空 `member` 触发了 `error code 65`
- 本次通过平台现有“用户手工同步到 LDAP”能力先将 `admin` 正常补回 `D001` 分组，再继续删除残留测试账号
- 最终 `D001` LDAP 分组恢复为只保留 `admin` 成员，没有遗留空组或脏引用

## 3. 验证结果

### 3.1 真实联调验证结果

本次已真实验证通过的场景：

- 启用 LDAP 用户登录 GitLab：通过
- 禁用 LDAP 用户登录 GitLab：拦截
- 启用用户错误密码登录 GitLab：拦截
- 不存在用户登录 GitLab：拦截
- GitLab 本地 `root` 账号标准登录：通过
- LDAP 首登自动创建 GitLab 本地用户：通过

### 3.2 缓存行为验证

本次还补做了一个缓存影响验证：

1. 先让启用样本账号登录 GitLab 成功
2. 再通过平台将该用户状态切换为禁用
3. 随后使用 GitLab 新会话立刻重新登录

结果：

- 平台 `/api/v1/ldap/precheck` 对禁用样本返回 `DISABLED_USER_FILTER_BLOCK = PASS`
- GitLab 新会话立即回到 `/users/sign_in`
- 本轮测试中未观察到“禁用后仍能继续登录”的延迟缓存现象

### 3.3 自动化验证

本次至少执行了以下自动化验证：

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd -Dtest=ThirdPartyLdapIntegrationApplicationServiceTest,PrototypeIntegrationTest#shouldExposeThirdPartyLdapFrameworkTemplateAndPrecheckEndpoints test
```

结果：

- Tests run: `4`
- Failures: `0`
- Errors: `0`

同时执行了 GitLab 容器内验证：

```bash
gitlab-rake gitlab:ldap:check
```

结果：

- GitLab 能正确列出当前允许访问的 LDAP 用户
- 修正后的 `user_filter` 已在 GitLab 运行配置中生效

## 4. 文档更新

本次同步更新了以下文档资产：

- `docs/templates/gitlab-ldap-template.md`
- `docs/runbooks/gitlab-ldap-joint-debug.md`

更新重点包括：

- GitLab 专属 `user_filter` 的正确写法
- GitLab 自动拼接 `uid=%{username}` 的行为说明
- 真实联调阶段应重点核对的字段
- 清理与回滚时需要保留的本地管理员入口说明

## 5. 下一步建议

下一轮建议继续推进：

1. 将这次真实联调中暴露的“最后一个组成员删除导致 `groupOfNames` 约束失败”补成正式代码修复，避免后续再次人工处理。
2. 把 GitLab 真实联调过程沉淀成标准化验收记录模板，便于 Jenkins、Nexus、禅道等系统复用。
3. 视需要增加一个“联调测试账号批量清理”运维脚本或受控接口，降低多轮联调后的环境收口成本。
