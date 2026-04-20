# 第三方系统 LDAP 通用接入框架设计

## 1. 背景

当前项目已经明确采用“平台维护 LDAP 目录数据，第三方系统直接连接 OpenLDAP 做认证”的整体方向，而不是由本平台代理 GitLab、Jenkins、Nexus、禅道等系统的登录链路。

这意味着第三方系统真实 LDAP 接入的核心问题，不是“如何再造一层认证服务”，而是“如何让多个异构系统在同一套 LDAP 契约下接入，并保持登录字段、启用规则、联调流程和验收标准一致”。

## 2. 设计目标

- 为 GitLab、Jenkins、Nexus、禅道等系统提供一套可复用的 LDAP 通用接入框架。
- 保持第三方系统运行时直接连接 OpenLDAP，平台不进入运行时认证链路。
- 统一登录标识为 `username(uid)`。
- 统一准入范围为“所有启用用户可登录”。
- 当前阶段只解决 LDAP 登录认证，不把 LDAP group 作为第三方系统统一授权来源。
- 将接入能力沉淀为可复用的目录契约、系统模板、联调预检和验收流程。

## 3. 非目标

- 不实现 SSO、免密跳转、登录态透传或统一会话。
- 不将第三方系统的 LDAP 配置托管在平台中。
- 不由平台代替第三方系统做本地权限分配。
- 不在当前阶段将 LDAP group 作为 GitLab 项目权限、Jenkins 角色、Nexus 权限或禅道业务权限的统一来源。

## 4. 现状约束

### 4.1 当前代码约束

- LDAP 基础配置由 [AppLdapProperties.java](/C:/Users/Administrator/ldap/src/main/java/com/company/idm/infrastructure/config/AppLdapProperties.java) 统一定义，当前默认目录根为 `dc=corp,dc=local`，用户 OU 为 `ou=people`，分组 OU 为 `ou=groups`。
- 用户 DN 由 [LdapDnHelper.java](/C:/Users/Administrator/ldap/src/main/java/com/company/idm/infrastructure/ldap/LdapDnHelper.java) 统一构造，形式为 `uid=<username>,ou=people,<baseDn>`。
- 平台当前对“禁用用户”的表达方式是 LDAP 属性 `employeeType=DISABLED`，不是删除 LDAP 用户。
- 平台自身登录可以拦截禁用用户，是因为平台先检查本地用户状态，再调用 LDAP 验密；第三方系统直连 LDAP 时，如果不加搜索过滤条件，禁用用户可能仍然能被检索到并继续登录。

### 4.2 已确认的业务边界

- 第三方系统只需要 LDAP 登录认证。
- 登录标识统一为 `username(uid)`。
- 所有启用用户允许直接登录第三方系统。
- 第三方系统配置仍由各系统手工维护，平台输出规范、模板和校验流程。

## 5. 方案对比

### 5.1 方案一：纯接入手册

- 只提供 GitLab、Jenkins、Nexus、禅道的 LDAP 配置说明和人工 checklist。
- 优点是交付快、实现轻。
- 缺点是缺少统一契约，容易随着系统增多出现配置漂移。

### 5.2 方案二：目录契约 + 应用模板 + 联调预检

- 平台定义统一 LDAP 契约。
- 每种第三方系统提供一份薄适配模板。
- 通过固定的预检、联调、验收流程保障一致性。
- 这是推荐方案。

### 5.3 方案三：平台托管第三方接入中心

- 平台保存第三方系统接入参数、责任人、审批和联调记录。
- 一致性更强，但复杂度更高，也不符合本次选择的职责边界。

## 6. 推荐方案

采用“目录契约 + 应用模板 + 联调预检”的控制面方案。

### 6.1 总体架构

- 运行时链路保持为：`第三方系统 -> OpenLDAP`。
- 平台只负责：
- 维护 LDAP 用户目录和状态。
- 定义统一目录契约。
- 输出第三方系统配置模板。
- 执行接入前预检和接入后验收。

### 6.2 架构分层

#### 6.2.1 目录契约层

统一定义：

- `baseDn`
- `peopleOu`
- `groupsOu`
- 登录字段
- 用户搜索过滤规则
- 必填 LDAP 属性
- 启用/禁用的语义

#### 6.2.2 应用模板层

为 GitLab、Jenkins、Nexus、禅道分别输出模板，但模板只允许在“连接参数”和“系统特定字段映射”上存在差异，不允许在“登录字段”和“启用过滤规则”上产生差异。

#### 6.2.3 联调预检层

在正式接入前统一检查目录连通性、Bind 账号可用性、用户搜索、禁用用户拦截和缓存行为。

#### 6.2.4 运维交付层

输出接入模板、联调记录单、验收清单和回滚说明，形成可复用资产。

## 7. LDAP 目录契约

### 7.1 基础目录规则

- `baseDn`: `dc=corp,dc=local`
- `userBase`: `ou=people,<baseDn>`
- `groupBase`: `ou=groups,<baseDn>`
- 用户 DN 规则：`uid=<username>,ou=people,<baseDn>`

### 7.2 统一登录字段

- 登录字段固定为 `uid`
- 第三方系统不得自行切换到邮箱、工号或手机号

### 7.3 统一用户过滤器

建议统一使用：

```ldap
(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))
```

含义：

- 只允许查找 `inetOrgPerson`
- 按 `uid` 匹配用户名
- 只允许 `employeeType=ENABLED` 的用户进入认证候选集合

### 7.4 标准属性映射

- 登录名：`uid`
- 显示名：`cn`
- 姓名补位：`sn`
- 邮箱：`mail`
- 手机：`mobile`
- 工号：`employeeNumber`
- 部门编码：`departmentNumber`
- 启停状态：`employeeType`

### 7.5 准入语义

- 启用用户：允许第三方系统登录
- 禁用用户：必须被统一用户过滤器拦截
- 删除用户：不作为第三方系统准入控制的主要手段

## 8. 应用模板层设计

### 8.1 通用模板模型

抽象一个 `ApplicationLdapProfile`：

- 连接参数
- 用户搜索参数
- 行为参数

建议包含字段：

- `ldap_host`
- `ldap_port`
- `tls_mode`
- `bind_dn`
- `bind_password`
- `base_dn`
- `user_base`
- `user_filter`
- `uid_attr`
- `name_attr`
- `email_attr`
- `auto_provision`
- `auth_cache_policy`
- `authorization_mode`

### 8.2 GitLab 适配

- 适合配置文件模板模式。
- 支持明确的 `user_filter` 配置。
- 建议开启“首登自动创建本地用户”，但授权仍在 GitLab 内部完成。

### 8.3 Jenkins 适配

- 适合后台 UI 字段映射模板模式。
- 必须显式替换默认用户搜索条件，避免仅以 `uid={0}` 作为搜索条件。
- 需要额外关注 LDAP 插件缓存与认证失败回显。

### 8.4 Nexus 适配

- 适合后台 UI 向导模板模式。
- 需要关注 LDAP Realm 启用顺序、用户缓存和失败时的回退行为。

### 8.5 禅道适配

- 当前只做适配槽位设计。
- 平台输出标准参数表、联调检查项和记录模板。
- 具体字段以目标版本官方手册二次确认为准。

### 8.6 统一授权边界

所有系统统一采用：

- `authorization_mode=LOCAL_ONLY`

即：

- LDAP 只负责认证
- 第三方系统本地权限体系负责授权

## 9. 联调预检与上线流程

### 9.1 阶段一：目录预检

检查项：

- OpenLDAP 主机和端口可达
- TLS 模式正确
- Bind 账号可用
- `ou=people` 可搜索
- `uid` 查询唯一
- 启用用户可被检索
- 禁用用户不可被检索

### 9.2 阶段二：模板预填

- 由平台输出标准模板
- 现场只替换环境变量值
- 最终生效配置必须留存脱敏快照

### 9.3 阶段三：认证联调

至少验证以下账号：

- 启用用户
- 禁用用户
- 错误密码用户
- 不存在用户
- 首登自动创建用户（若该系统支持）

### 9.4 阶段四：边界验证

- 验证禁用后的缓存失效时间
- 验证 TLS/Bind/过滤配置错误时的报错路径
- 验证平台不在运行时链路中时第三方系统仍可认证

### 9.5 阶段五：上线验收

验收单至少包含：

- 应用名称
- 环境
- 责任人
- 模板版本
- 最终 `user_base`
- 最终 `user_filter`
- 标准测试账号结果
- 缓存结论
- 回滚步骤

## 10. 标准交付物

- 第三方系统 LDAP 参数模板
- 联调记录单
- 验收清单
- 回滚说明
- 常见问题库

## 11. 安全红线

- Bind 账号必须是只读账号，不能复用 LDAP 管理员账号。
- LDAP bind 密码不得写入仓库、设计正文或截图。
- 所有第三方系统必须统一使用包含 `employeeType=ENABLED` 的用户过滤器。
- 不允许第三方系统自行改成邮箱、工号或手机号作为登录字段。
- 不允许把 LDAP 认证成功直接等价为业务授权成功。
- 不允许跳过“禁用用户不可登录”这条验收项。

## 12. 风险与应对

### 12.1 禁用用户绕过风险

风险：

- 如果第三方系统只做 `uid` 搜索，不带 `employeeType=ENABLED` 过滤，禁用用户可能仍然可登录。

应对：

- 强制把过滤器纳入统一模板和验收项。

### 12.2 系统配置漂移风险

风险：

- 不同应用管理员可能手工改动搜索 base、登录字段或 TLS 模式。

应对：

- 统一模板版本化管理。
- 验收单强制记录最终生效参数。

### 12.3 缓存延迟风险

风险：

- 部分系统存在认证缓存，禁用用户不会立即失效。

应对：

- 将缓存验证纳入联调。
- 在模板中注明缓存刷新策略。

### 12.4 禅道产品资料不确定风险

风险：

- 当前公开资料不足以证明所有 LDAP 字段映射细节。

应对：

- 先保留适配槽位。
- 接入具体禅道版本时，依据目标版本文档和现场联调结果封板。

## 13. 对当前仓库的建议落地

建议在当前仓库后续补充：

- 在 [docs/project-design.md](/C:/Users/Administrator/ldap/docs/project-design.md) 中新增“第三方系统 LDAP 通用接入框架”章节。
- 在 `docs/templates/` 下增加：
- `gitlab-ldap-template.md`
- `jenkins-ldap-template.md`
- `nexus-ldap-template.md`
- `zentao-ldap-template.md`
- 在 `docs/checklists/` 下增加：
- `ldap-precheck.md`
- `ldap-acceptance.md`
- 在 `docs/runbooks/` 下增加：
- `third-party-ldap-rollback.md`

## 14. 结论

这套通用接入框架的本质不是“做一个新的认证系统”，而是“把第三方系统直接接 LDAP 的能力标准化”。其核心资产是统一 LDAP 契约、统一用户过滤规则、统一模板、统一预检和统一验收流程。

在当前边界下，这个方案可以稳定支撑 GitLab、Jenkins、Nexus、禅道等系统复用同一身份源，同时避免平台进入运行时认证链路，符合“统一身份认证，而非 SSO”的总体设计方向。
