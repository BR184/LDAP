# 项目进展记录 008 - 关键方法注释与设计文档补强

## 1. 里程碑说明

本次完成了两项工作：

1. 按照阿里巴巴 Java 编程规范的思路，对核心业务方法补充方法级注释
2. 补强并校正文档 `project-design.md` 中此前未覆盖或考虑不充分的设计细节

完成时间：2026-04-16

## 2. 代码注释补充内容

本次重点对以下关键方法增加了方法级注释或关键块说明：

- `UserApplicationService`
  - `listUsers`
  - `createUser`
  - `updateUser`
  - `updateStatus`
  - `deleteUser`
  - `changePassword`
  - `resetPassword`
  - `nextTokenVersion`
- `AuthApplicationService`
  - `login`
  - `loadProfile`
  - `failure`
- `SqlLogInterceptor`
  - `writeSqlLog`
  - `extractParameters`
- `GlobalExceptionHandler`
  - `resolveBizStatus`
  - `extractBindingMessage`

### 2.1 注释原则

本次注释遵循以下约束：

- 只注释“有业务分支、状态变化、边界约束”的重要方法
- 不为简单 getter/setter/record 访问器机械增加低价值注释
- 注释关注“为什么这么做”和“这段逻辑的约束是什么”，而不只是重复代码字面意思

## 3. 设计文档补强内容

本次对 `project-design.md` 主要补充了以下设计细节：

### 3.1 用户管理设计补强

新增或完善：

- 用户更新流程
- 用户删除流程
- 密码管理设计
- 当前原型已落地能力说明

并同步调整业务规则：

- 删除用户：LDAP 物理删除 + MySQL 逻辑删除
- 密码策略：最少 6 位，允许弱口令和纯数字
- 管理员重置密码：固定为 `123456`
- 改密/重置/删除后均通过 `token_version` 失效旧 token

### 3.2 LDAP 设计补强

补充了 `deleteUser` 职责，明确当前用户删除需要对 LDAP 做物理删除。

### 3.3 数据库设计补强

补充了 `sys_user.token_version` 字段设计说明，明确其用于登录态失效控制。

### 3.4 接口设计补强

将认证接口与用户管理接口划分为：

- 当前已落地接口
- 后续预留接口

避免文档中的目标清单与当前实现状态混淆。

### 3.5 SQL 日志与 traceId 设计补强

新增了 SQL 日志与链路追踪设计建议，明确：

- MyBatis 拦截器输出 SQL 日志
- 慢 SQL 分级输出
- 敏感参数脱敏
- `traceId` 贯穿控制层日志、SQL 日志与审计日志

### 3.6 原型环境说明补强

补充说明：

- 原型阶段可使用 H2 内存库快速联调
- 正式环境目标数据库仍为 MySQL
- LDAP 支持 `stub` 与 `spring` 两种运行模式

## 4. 当前效果

本次补强后，设计文档与当前代码实现的一致性更高，尤其在以下方面更加清晰：

- 当前已经实现到什么程度
- 哪些接口仍然只是后续预留
- 用户删除、改密、重置密码等敏感流程的实际规则
- SQL 日志、traceId、token 失效机制等支撑能力

## 5. 说明

本次修改主要涉及代码注释与设计文档，不改变现有业务行为。
