# 手机号初始密码 Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 所有首次创建或补建 LDAP 用户统一使用手机号作为初始密码，缺失或非法手机号回退 `123456`，密码重置行为保持不变。

**Architecture:** 在用户应用层建立无状态 `InitialPasswordPolicy` 作为唯一规则入口，各账号创建路径显式依赖该策略。删除客户端可提交的 `initialPassword` 字段，保持重置密码服务使用独立常量。

**Tech Stack:** Java 17、Spring Boot 3、Spring LDAP、JUnit 5、Mockito、Vue 3、TypeScript。

---

### Task 1: Create the authoritative initial-password policy

**Files:**
- Create: `src/main/java/com/company/idm/application/user/InitialPasswordPolicy.java`
- Test: `src/test/java/com/company/idm/application/user/InitialPasswordPolicyTest.java`

**Step 1: Write failing tests**

Cover a valid 11-digit mobile, whitespace-normalized mobile, null/blank mobile, and invalid mobile.

**Step 2: Run tests and verify RED**

Run: `E:\ldap\.tools\apache-maven-3.9.6\bin\mvn.cmd -Dtest=InitialPasswordPolicyTest test`

Expected: compilation failure because the policy does not exist.

**Step 3: Implement the policy**

Return the trimmed mobile for `^1\d{10}$`; otherwise return the fallback `123456`. Do not log or expose the result.

**Step 4: Run tests and verify GREEN**

Run the same Maven command. Expected: all policy tests pass.

### Task 2: Migrate every LDAP creation path

**Files:**
- Modify: `src/main/java/com/company/idm/application/user/UserApplicationService.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportExecutionApplicationService.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportRollbackApplicationService.java`
- Modify: `src/main/java/com/company/idm/application/sync/handler/LdapReconcileUserHandler.java`
- Test: `src/test/java/com/company/idm/application/sync/importplan/ImportExecutionApplicationServiceTest.java`

**Step 1: Extend failing service tests**

Assert that an imported user with a mobile passes that mobile to `createOrUpdateUser`; assert missing mobile passes `123456`.

**Step 2: Run targeted tests and verify RED**

Expected: existing implementation passes hard-coded `123456` for users with valid mobiles.

**Step 3: Inject and use `InitialPasswordPolicy`**

Resolve the initial password immediately before LDAP create/create-or-update calls. Keep all reset-password constants and calls unchanged.

**Step 4: Run targeted tests and verify GREEN**

Expected: all targeted tests pass.

### Task 3: Remove client-controlled initial password

**Files:**
- Modify: `src/main/java/com/company/idm/application/user/CreateUserCommand.java`
- Modify: `src/main/java/com/company/idm/interfaces/user/CreateUserRequest.java`
- Modify: `src/main/java/com/company/idm/interfaces/user/UserController.java`
- Modify: `frontend/src/types/user.ts`
- Modify: `frontend/src/views/user/components/UserFormDrawer.vue`

**Step 1: Delete the retired field**

Remove `initialPassword` from API request, command constructors, frontend payload and form UI. Update all callers directly; add no aliases or compatibility path.

**Step 2: Search for stale symbols**

Run: `rg -n "initialPassword|DEFAULT_INITIAL_PASSWORD" src/main frontend/src`

Expected: no stale create-user password input or constant remains.

**Step 3: Build frontend**

Run: `npm run build` in `frontend`. Expected: exit code 0.

### Task 4: Full verification and restart

**Files:**
- Verify only

**Step 1: Verify reset behavior remains isolated**

Search reset services and UI for `123456`; confirm reset constants and messages remain.

**Step 2: Run backend build**

Run: `E:\ldap\.tools\apache-maven-3.9.6\bin\mvn.cmd clean package`. Expected: all tests pass.

**Step 3: Restart backend and frontend**

Run `scripts/start-backend-dev.ps1`, restart Vite, then verify health and login APIs through HTTP without browser automation.
