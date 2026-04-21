# 项目进展记录 007 - 用户管理模块扩展

## 1. 里程碑说明

本次基于用户管理模块现有原型，补充完成了以下能力的最小落地实现：

- 更新用户
- 删除用户
- 修改本人密码
- 管理员重置用户密码

并同步应用了新的业务规则：

- 删除用户时：LDAP 物理删除，MySQL 逻辑删除
- 密码规则调整为：最少 6 位，允许弱口令，允许纯数字
- 重置密码时：仅管理员允许操作，固定重置为 `123456`，后续不做首次强制改密

完成时间：2026-04-16

## 2. 本次完成内容

### 2.1 新增用户管理接口

已新增如下接口：

- `PUT /api/v1/users/{id}`：更新用户基础资料
- `DELETE /api/v1/users/{id}`：删除用户
- `PUT /api/v1/users/me/password`：用户本人修改密码
- `PUT /api/v1/users/{id}/password/reset`：管理员重置用户密码

### 2.2 用户删除规则

已实现：

- 先在 LDAP 中对用户做物理删除
- 再在 MySQL 中做逻辑删除
- 同时更新 `tokenVersion`，使旧 token 失效

### 2.3 密码规则调整

已实现新的最小密码规则：

- 密码不能为空
- 密码长度至少 6 位
- 不要求大小写字母、数字、特殊字符组合
- 纯数字密码可通过校验

该规则已应用到：

- 创建用户初始密码校验
- 用户修改密码校验
- 管理员重置密码生成结果校验

### 2.4 重置密码规则

已实现：

- 仅 `SUPER_ADMIN` 可执行重置密码
- 固定重置密码为 `123456`
- 不增加首次登录强制改密逻辑
- 重置密码后递增 `tokenVersion`

## 3. 技术改动点

本次新增或扩展了以下关键组件：

- `UpdateUserCommand`
- `DeleteUserCommand`
- `ChangePasswordCommand`
- `ResetPasswordCommand`
- `UpdateUserRequest`
- `ChangePasswordRequest`
- `ResetPasswordResponse`
- `PasswordPolicyValidator`
- `SimplePasswordPolicyValidator`

并扩展了：

- `UserApplicationService`
- `UserController`
- `UserRepository`
- `MybatisUserRepository`
- `LdapDirectoryService`
- `StubLdapDirectoryService`
- `SpringLdapDirectoryService`

同时新增数据库迁移脚本：

- `V2__user_management_completion.sql`

用于补充用户更新、删除、密码重置接口对应的权限点。

## 4. 测试验证

本次补充了单元测试与集成测试，验证以下场景：

- 创建用户时密码规则校验
- 更新用户资料
- 删除用户后 LDAP 物理删除与 MySQL 逻辑删除
- 修改本人密码成功与失败分支
- 管理员重置密码成功
- 非管理员重置密码失败
- 接口级更新、改密、重置密码、删除流程

执行命令：

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

测试结果：通过

## 5. 当前状态

至此，用户管理模块已经具备以下主要能力：

- 手动创建用户
- 获取用户列表
- 更新用户状态
- 用户登录
- 获取当前用户信息
- 更新用户
- 删除用户
- 修改本人密码
- 管理员重置用户密码

## 6. 下一步建议

下一轮可继续补充：

1. 用户详情查询接口
2. 用户查询筛选与分页
3. 用户恢复/再次入职恢复逻辑
4. 更完整的 LDAP 异常补偿与同步状态跟踪
