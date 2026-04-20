# Third-Party LDAP Integration Framework Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 把“第三方系统 LDAP 通用接入框架”从确认后的设计落成仓库内可执行的标准文档资产，包括总设计章节、应用模板、联调清单和回滚手册。

**Architecture:** 本次实现不改运行时认证代码，重点交付控制面文档资产。核心做法是把统一 LDAP 契约写回主设计文档，再按“通用契约 -> 应用模板 -> 联调/验收 -> 回滚手册”拆成独立文档，形成后续 GitLab、Jenkins、Nexus、禅道接入时可复用的标准包。

**Tech Stack:** Markdown, PowerShell, ripgrep, git

---

### Task 1: 将通用接入框架写回主设计文档

**Files:**
- Modify: `docs/project-design.md`
- Reference: `docs/plans/2026-04-20-third-party-ldap-integration-design.md`

**Step 1: 验证主设计文档中还没有该章节**

Run:

```powershell
rg -n "第三方系统 LDAP 通用接入框架" docs/project-design.md
```

Expected: 无匹配结果。

**Step 2: 在主设计文档中增加新章节**

添加一节完整内容，至少覆盖：

```markdown
## X. 第三方系统 LDAP 通用接入框架

### 目标
- 第三方系统直接连 OpenLDAP
- 平台不进入运行时认证链路
- 登录字段统一为 `uid`
- 所有启用用户可登录

### 目录契约
- `baseDn`
- `peopleOu`
- `groupsOu`
- 标准过滤器 `(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`

### 应用模板
- GitLab
- Jenkins
- Nexus
- 禅道

### 联调与验收
- 目录预检
- 认证联调
- 边界验证
- 上线验收
```

**Step 3: 自查文档引用与术语一致性**

Run:

```powershell
rg -n "uid|employeeType=ENABLED|统一身份认证|不是 SSO" docs/project-design.md
```

Expected: 新章节中的核心术语都能被检索到。

**Step 4: 提交最小变更**

Run:

```powershell
git add docs/project-design.md
git commit -m "docs: add third-party LDAP framework section"
```

Expected: 只提交主设计文档更新。

### Task 2: 新增通用 LDAP 契约模板

**Files:**
- Create: `docs/templates/ldap-common-contract.md`
- Create: `docs/templates/README.md`

**Step 1: 先确认目录不存在**

Run:

```powershell
Get-ChildItem docs/templates
```

Expected: 报路径不存在，或为空目录。

**Step 2: 创建通用契约模板**

在 `docs/templates/ldap-common-contract.md` 写入：

```markdown
# LDAP Common Contract

## Fixed Rules
- login_attr: `uid`
- user_base: `ou=people,<baseDn>`
- group_base: `ou=groups,<baseDn>`
- user_filter: `(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`

## Required Attributes
- `uid`
- `cn`
- `sn`
- `mail`
- `employeeType`

## Security Rules
- bind 账号必须只读
- bind 密码不得入库
- 禁用用户必须被过滤器拦截
```

在 `docs/templates/README.md` 写入模板索引和使用顺序。

**Step 3: 校验模板内容**

Run:

```powershell
rg -n "user_filter|bind 账号|uid" docs/templates/ldap-common-contract.md docs/templates/README.md
```

Expected: 关键规则全部能命中。

**Step 4: 提交最小变更**

Run:

```powershell
git add docs/templates/ldap-common-contract.md docs/templates/README.md
git commit -m "docs: add common LDAP contract templates"
```

Expected: 只提交模板目录初始化和通用契约文档。

### Task 3: 新增 GitLab、Jenkins、Nexus、禅道模板

**Files:**
- Create: `docs/templates/gitlab-ldap-template.md`
- Create: `docs/templates/jenkins-ldap-template.md`
- Create: `docs/templates/nexus-ldap-template.md`
- Create: `docs/templates/zentao-ldap-template.md`
- Reference: `docs/templates/ldap-common-contract.md`

**Step 1: 先确认 4 份模板都不存在**

Run:

```powershell
rg --files docs/templates
```

Expected: 只有 `README.md` 和 `ldap-common-contract.md`。

**Step 2: 写 GitLab 模板**

至少包含：

```markdown
# GitLab LDAP Template

## Variables
- `host`
- `port`
- `bind_dn`
- `bind_password`
- `base`
- `user_filter`

## Fixed Mapping
- uid field -> `uid`
- name field -> `cn`
- email field -> `mail`

## Notes
- 允许首登自动创建本地用户
- 权限仍在 GitLab 本地维护
```

**Step 3: 写 Jenkins、Nexus、禅道模板**

统一结构：

```markdown
# <System> LDAP Template

## Scope
- 仅认证
- 不托管授权

## Fixed Contract
- login_attr: `uid`
- user_filter: `(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`

## Field Mapping
- LDAP URL
- User Base
- User Search Filter
- Display Name
- Email

## Product Notes
- 写明该产品的缓存、首登、本地权限边界
```

禅道模板必须显式写明：

```markdown
> 待目标版本官方手册确认最终字段名，本模板先作为联调占位稿。
```

**Step 4: 校验四份模板的一致性**

Run:

```powershell
rg -n "employeeType=ENABLED|仅认证|uid" docs/templates/gitlab-ldap-template.md docs/templates/jenkins-ldap-template.md docs/templates/nexus-ldap-template.md docs/templates/zentao-ldap-template.md
```

Expected: 四份模板都包含统一登录和过滤规则。

**Step 5: 提交最小变更**

Run:

```powershell
git add docs/templates/gitlab-ldap-template.md docs/templates/jenkins-ldap-template.md docs/templates/nexus-ldap-template.md docs/templates/zentao-ldap-template.md
git commit -m "docs: add third-party LDAP application templates"
```

Expected: 只提交四份应用模板。

### Task 4: 新增联调预检和验收清单

**Files:**
- Create: `docs/checklists/ldap-precheck.md`
- Create: `docs/checklists/ldap-acceptance.md`
- Reference: `docs/templates/ldap-common-contract.md`

**Step 1: 验证 checklist 目录未创建**

Run:

```powershell
Get-ChildItem docs/checklists
```

Expected: 报路径不存在，或为空目录。

**Step 2: 写预检清单**

在 `docs/checklists/ldap-precheck.md` 中写入：

```markdown
# LDAP Precheck

- [ ] LDAP 主机和端口可达
- [ ] TLS 模式已确认
- [ ] 只读 bind 账号可用
- [ ] `ou=people` 可检索
- [ ] 启用用户可命中
- [ ] 禁用用户不可命中
- [ ] `uid` 唯一
```

**Step 3: 写验收清单**

在 `docs/checklists/ldap-acceptance.md` 中写入：

```markdown
# LDAP Acceptance

- [ ] enabled 用户可登录
- [ ] disabled 用户不可登录
- [ ] 错误密码不可登录
- [ ] 不存在用户不可登录
- [ ] 缓存行为已记录
- [ ] 回滚方案已验证
- [ ] 最终生效配置已脱敏归档
```

**Step 4: 校验强制项存在**

Run:

```powershell
rg -n "disabled 用户不可登录|只读 bind 账号|缓存行为已记录" docs/checklists/ldap-precheck.md docs/checklists/ldap-acceptance.md
```

Expected: 三个关键条目都能命中。

**Step 5: 提交最小变更**

Run:

```powershell
git add docs/checklists/ldap-precheck.md docs/checklists/ldap-acceptance.md
git commit -m "docs: add LDAP precheck and acceptance checklists"
```

Expected: 只提交 checklist 文档。

### Task 5: 新增回滚手册

**Files:**
- Create: `docs/runbooks/third-party-ldap-rollback.md`
- Reference: `docs/checklists/ldap-acceptance.md`

**Step 1: 先确认 runbook 目录不存在**

Run:

```powershell
Get-ChildItem docs/runbooks
```

Expected: 报路径不存在，或为空目录。

**Step 2: 写回滚手册**

在 `docs/runbooks/third-party-ldap-rollback.md` 中写入：

```markdown
# Third-Party LDAP Rollback

## Trigger
- 用户大面积无法登录
- bind 账号异常
- TLS 证书配置错误

## Rollback Steps
1. 关闭目标系统 LDAP 认证入口
2. 恢复目标系统原本地认证配置
3. 验证管理员账户可登录
4. 记录故障时间、影响范围、回滚责任人

## Evidence
- 错误截图脱敏保存
- 最终回滚配置脱敏保存
```

**Step 3: 校验 runbook 关键字段**

Run:

```powershell
rg -n "Rollback Steps|关闭目标系统 LDAP 认证入口|脱敏保存" docs/runbooks/third-party-ldap-rollback.md
```

Expected: 三类关键说明都能命中。

**Step 4: 提交最小变更**

Run:

```powershell
git add docs/runbooks/third-party-ldap-rollback.md
git commit -m "docs: add third-party LDAP rollback runbook"
```

Expected: 只提交回滚文档。

### Task 6: 做一轮全量文档自检并收尾

**Files:**
- Modify: `docs/project-design.md`
- Verify: `docs/templates/README.md`
- Verify: `docs/templates/*.md`
- Verify: `docs/checklists/*.md`
- Verify: `docs/runbooks/*.md`

**Step 1: 全量检索核心关键词**

Run:

```powershell
rg -n "uid|employeeType=ENABLED|统一身份认证|不是 SSO|只读 bind|disabled 用户不可登录" docs
```

Expected: 主设计文档、模板、checklist、runbook 中都有命中结果。

**Step 2: 人工检查重复和冲突**

重点检查：

- `uid` 是否被某份模板改成邮箱或工号
- 是否有文档把 LDAP group 误写成当前授权来源
- 是否有文档把平台写成运行时认证代理

**Step 3: 生成最终交付提交**

Run:

```powershell
git add docs/project-design.md docs/templates docs/checklists docs/runbooks
git commit -m "docs: finalize third-party LDAP integration framework docs"
```

Expected: 所有通用接入框架文档一起入库。
