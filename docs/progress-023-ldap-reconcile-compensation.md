# 项目进展记录 023 - MySQL 与 LDAP 对账补偿增强

## 1. 里程碑说明

本次完成了 MySQL 与 LDAP 对账补偿能力从“粗粒度缺失检查”向“部门、用户、成员关系三层细粒度对账”的升级落地。

完成时间：2026-04-20

## 2. 本次完成内容

### 2.1 LDAP 读能力补充

为支撑字段级和关系级对账，本次补充了 LDAP 查询能力：

- `LdapUserSnapshot`
- `LdapGroupSnapshot`
- LDAP 用户快照查询
- LDAP 分组快照查询
- LDAP 用户全量枚举
- LDAP 分组全量枚举
- 用户所属 LDAP group 查询

这些能力已在 `stub` 与 `spring` 两种实现中同步补齐。

### 2.2 差异类型增强

在原有基础上扩展了以下差异类型：

- `STATUS_MISMATCH`
- `DN_MISMATCH`

这样当前可表达的差异包括：

- `MISSING_IN_MYSQL`
- `MISSING_IN_LDAP`
- `FIELD_MISMATCH`
- `RELATION_MISMATCH`
- `STATUS_MISMATCH`
- `DN_MISMATCH`

### 2.3 对账处理器增强

当前已形成三类对账处理器：

- `LdapReconcileDepartmentHandler`
- `LdapReconcileUserHandler`
- `LdapReconcileMembershipHandler`

具体覆盖：

- 部门缺失、部门字段、部门 DN、孤儿 group
- 用户缺失、用户字段、用户状态、用户 DN、孤儿用户
- 用户与部门 group 的成员关系差异

### 2.4 补偿策略落地

当前已支持自动补偿的场景：

- LDAP 缺失部门 group
- LDAP 缺失用户
- 部门字段与 DN 不一致
- 用户字段、状态、DN 不一致
- 用户与部门 group 成员关系不一致

当前仅记录、不自动删除的场景：

- LDAP 中存在但 MySQL 中不存在的孤儿用户
- LDAP 中存在但 MySQL 中不存在的孤儿 group

## 3. 同步编排调整

LDAP 对账执行顺序已调整为：

1. 部门对账
2. 用户对账
3. 成员关系对账

并在 `SyncApplicationService` 中统一编排。

## 4. 测试验证

本次新增并补充了以下测试：

- `LdapReconcileUserHandlerTest`
- `LdapReconcileMembershipHandlerTest`
- 原有部门对账测试适配增强

本次改动后已执行全量测试，并全部通过。

执行命令：

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

测试结果：

- Tests run: `92`
- Failures: `0`
- Errors: `0`

## 5. 文档更新

已同步更新：

- `docs/project-design.md`

补充内容包括：

- MySQL 与 LDAP 对账补偿设计章节
- 当前原型落地说明
- 新增测试文件清单
- 最新测试结果

## 6. 下一步建议

下一轮建议继续推进：

1. 对账结果前端展示
2. 更细粒度的修复动作日志
3. 定时任务失败重试与告警
4. GitLab 真实 LDAP 接入联调
