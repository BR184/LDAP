# 项目进展记录 025 - 用户详情与手工同步 LDAP

## 1. 里程碑说明

本次完成了预留接口中的“用户详情接口”和“手工同步 LDAP 接口”落地，并同步补充了部门手工同步 LDAP 能力。

完成时间：2026-04-20

## 2. 本次完成内容

### 2.1 用户详情接口

已新增：

- `GET /api/v1/users/{id}`

实现能力：

- 查询单个用户详情
- 返回用户基础信息、部门编码、LDAP DN、角色编码
- 延续统一响应结构与接口鉴权规则

### 2.2 手工同步 LDAP 接口

已新增：

- `POST /api/v1/users/{id}/sync-ldap`
- `POST /api/v1/departments/{deptCode}/sync-ldap`

实现能力：

- 用户手工同步：
  - 用户不存在则报错
  - LDAP 用户不存在时补建
  - LDAP 用户存在时更新
  - 同步启停状态
  - 同步部门 group 成员关系
  - 回写最新 `ldapDn`

- 部门手工同步：
  - 部门不存在则报错
  - LDAP group 不存在时补建
  - LDAP group 存在时更新
  - 同步部门成员关系
  - 回写最新 `ldapDn`

### 2.3 权限与数据库迁移

新增 Flyway 脚本：

- `V8__manual_ldap_sync_and_user_detail.sql`

新增权限点：

- `USER_DETAIL`
- `USER_SYNC_LDAP`
- `DEPT_SYNC_LDAP`

并已授予 `ADMIN`。

## 3. 测试验证

本次补充并通过的测试包括：

- `UserApplicationServiceTest`
  - 用户详情查询
  - 用户手工同步 LDAP

- `DepartmentApplicationServiceTest`
  - 部门手工同步 LDAP

- `PrototypeIntegrationTest`
  - 用户详情接口
  - 用户手工同步 LDAP 接口
  - 部门手工同步 LDAP 接口

执行命令：

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

测试结果：

- Tests run: `96`
- Failures: `0`
- Errors: `0`

## 4. 文档更新

已同步更新：

- `docs/project-design.md`

主要更新内容包括：

- 用户管理接口当前已落地清单
- 部门管理接口当前已落地清单
- 第二期“预留接口实现”完成项
- 当前全量测试结果

## 5. 下一步建议

下一轮建议继续推进：

1. 前端用户管理页与分组管理页同步按钮接入
2. 对账结果前端展示
3. GitLab 真实 LDAP 接入联调
