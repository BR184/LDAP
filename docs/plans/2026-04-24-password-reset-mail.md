# Password Reset Mail Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将管理员重置密码和忘记密码改造成“随机 6 位数字密码 + LDAP 改密 + 邮件通知”的统一流程，并移除前端明文密码展示。

**Architecture:** 以统一的 `PasswordResetApplicationService` 收口重置逻辑，复用 LDAP 改密、tokenVersion 失效、邮件通知和审计日志能力。登录页新增忘记密码入口，用户管理页调整为只提示邮件已发送。

**Tech Stack:** Spring Boot 3、Spring Security、Spring Mail、MyBatis-Plus、Vue 3、Element Plus、JUnit 5、Mockito

---

### Task 1: 补充设计文档

**Files:**
- Create: `docs/plans/2026-04-24-password-reset-mail-design.md`
- Create: `docs/plans/2026-04-24-password-reset-mail.md`

**Step 1: 写入设计文档**

- 记录密码重置邮件方案、限流策略、邮件文案与风险说明。

**Step 2: 写入实现计划**

- 把后端、前端、测试和文档任务拆成可执行步骤。

### Task 2: 编写后端失败测试

**Files:**
- Modify: `src/test/java/com/company/idm/test/AuthControllerTest.java`
- Modify: `src/test/java/com/company/idm/test/UserControllerTest.java`
- Create: `src/test/java/com/company/idm/test/PasswordResetApplicationServiceTest.java`

**Step 1: 为忘记密码控制器写失败测试**

- 校验公开接口可访问。
- 校验统一成功文案。

**Step 2: 为管理员重置密码响应改造写失败测试**

- 断言不再返回 `resetPassword` 字段。

**Step 3: 为应用服务写失败测试**

- 正常重置会生成 6 位数字、改 LDAP、发邮件、递增 tokenVersion。
- 忘记密码遇到不存在用户、无邮箱、禁用用户时不会抛给外层。
- 限流命中时不会执行 LDAP 和邮件发送。

**Step 4: 运行对应测试确认失败**

Run:

```powershell
& '.\.tools\apache-maven-3.9.6\bin\mvn.cmd' -q "-Dtest=AuthControllerTest,UserControllerTest,PasswordResetApplicationServiceTest" test
```

### Task 3: 实现后端密码重置与邮件发送

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/company/idm/interfaces/auth/AuthController.java`
- Modify: `src/main/java/com/company/idm/interfaces/user/UserController.java`
- Modify: `src/main/java/com/company/idm/infrastructure/security/SecurityConfig.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-local.yml`
- Modify: `src/main/resources/application-dev.yml`
- Modify: `src/main/resources/application-test.yml`
- Modify: `src/main/resources/application-prod.yml`
- Create: `src/main/java/com/company/idm/interfaces/auth/ForgotPasswordRequest.java`
- Create: `src/main/java/com/company/idm/application/user/PasswordResetApplicationService.java`
- Create: `src/main/java/com/company/idm/application/user/ForgotPasswordCommand.java`
- Create: `src/main/java/com/company/idm/application/user/AdminResetPasswordCommand.java`
- Create: `src/main/java/com/company/idm/domain/user/PasswordGenerator.java`
- Create: `src/main/java/com/company/idm/domain/user/PasswordResetNotificationService.java`
- Create: `src/main/java/com/company/idm/domain/user/PasswordResetThrottleService.java`
- Create: `src/main/java/com/company/idm/infrastructure/security/SecureRandomPasswordGenerator.java`
- Create: `src/main/java/com/company/idm/infrastructure/mail/SmtpPasswordResetNotificationService.java`
- Create: `src/main/java/com/company/idm/infrastructure/mail/NoopPasswordResetNotificationService.java`
- Create: `src/main/java/com/company/idm/infrastructure/security/InMemoryPasswordResetThrottleService.java`
- Create: `src/main/java/com/company/idm/infrastructure/config/PasswordResetProperties.java`

**Step 1: 加入邮件依赖与配置**

- 引入 `spring-boot-starter-mail`
- 增加密码重置主题、发件人、限流参数配置

**Step 2: 实现领域支撑组件**

- 实现密码生成器、邮件通知接口和内存限流器

**Step 3: 实现统一密码重置应用服务**

- 管理员重置
- 忘记密码
- 审计记录
- tokenVersion 失效

**Step 4: 调整控制器和安全放行**

- 新增 `/api/v1/auth/password/forgot`
- 管理员重置接口返回空响应

**Step 5: 运行测试确认通过**

Run:

```powershell
& '.\.tools\apache-maven-3.9.6\bin\mvn.cmd' -q "-Dtest=AuthControllerTest,UserControllerTest,PasswordResetApplicationServiceTest" test
```

### Task 4: 编写前端失败检查并实现界面改造

**Files:**
- Modify: `frontend/src/views/auth/LoginView.vue`
- Modify: `frontend/src/api/modules/auth.ts`
- Modify: `frontend/src/types/auth.ts`
- Modify: `frontend/src/views/user/UserListView.vue`
- Modify: `frontend/src/api/modules/user.ts`
- Modify: `frontend/src/types/user.ts`

**Step 1: 改造前端类型与 API**

- 新增忘记密码请求
- 管理员重置密码接口改为 `void`

**Step 2: 改造登录页**

- 增加忘记密码入口和用户名输入弹窗

**Step 3: 改造用户管理页**

- 删除明文密码展示弹窗
- 替换为邮件发送成功提示

**Step 4: 运行前端类型检查**

Run:

```powershell
npm.cmd run check
```

### Task 5: 更新总设计文档与整体验证

**Files:**
- Modify: `docs/project-design.md`

**Step 1: 更新总设计文档**

- 补充密码重置邮件方案
- 补充忘记密码入口
- 补充邮件配置与限流约束

**Step 2: 运行后端编译和核心测试**

Run:

```powershell
& '.\.tools\apache-maven-3.9.6\bin\mvn.cmd' -q -DskipTests compile
& '.\.tools\apache-maven-3.9.6\bin\mvn.cmd' -q "-Dtest=AuthControllerTest,UserControllerTest,PasswordResetApplicationServiceTest,AuthApplicationServiceTest" test
```

**Step 3: 运行前端检查**

Run:

```powershell
npm.cmd run check
```
