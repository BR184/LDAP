# 删除首页与个人中心部门路径 Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 删除所有首页入口，并让个人中心展示完整部门层级路径。

**Architecture:** 后端以 `deptCode` 查询部门祖先链，将完整路径作为 `departmentPath` 返回，同时保持编码和叶子名称字段语义不变。前端删除首页路由和导航定义，所有认证后的默认入口统一为个人中心。

**Tech Stack:** Spring Boot 3、MyBatis-Plus、Vue 3、TypeScript、Vue Router、Vite、JUnit 5。

---

### Task 1: 建立部门路径后端契约

**Files:**
- Modify: `src/main/java/com/company/idm/domain/user/User.java`
- Modify: `src/main/java/com/company/idm/application/user/UserApplicationService.java`
- Modify: `src/main/java/com/company/idm/application/auth/AuthApplicationService.java`
- Modify: `src/main/java/com/company/idm/interfaces/auth/CurrentUserResponse.java`
- Modify: `src/main/java/com/company/idm/interfaces/auth/AuthController.java`
- Test: `src/test/java/com/company/idm/application/auth/AuthApplicationServiceTest.java`

**Step 1: Write the failing test**

Mock a user whose department ancestor chain is `ROOT -> RND -> PLATFORM`; assert the profile response domain object has `departmentPath` equal to `总部 / 研发中心 / 平台研发部`.

**Step 2: Run test to verify it fails**

Run: `E:\ldap\.tools\apache-maven-3.9.6\bin\mvn.cmd -Dtest=AuthApplicationServiceTest test`

Expected: FAIL because no `departmentPath` is calculated.

**Step 3: Write minimal implementation**

Add `departmentPath` to the user/profile contract. Reuse the department repository's authoritative hierarchy data in the application layer, preserving `deptCode` as ID and `deptName` as leaf name.

**Step 4: Run test to verify it passes**

Run: `E:\ldap\.tools\apache-maven-3.9.6\bin\mvn.cmd -Dtest=AuthApplicationServiceTest test`

Expected: PASS.

### Task 2: Remove dashboard navigation and route

**Files:**
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/constants/navigation.ts`
- Modify: `frontend/src/layout/AdminLayout.vue`
- Modify: `frontend/src/stores/auth.ts`
- Modify: `frontend/src/stores/menu.ts`
- Delete: `frontend/src/views/dashboard/DashboardView.vue`

**Step 1: Remove obsolete references**

Delete the dashboard route, navigation item, administrator sidebar item, dashboard-specific authorization condition and administrator default entry behavior.

**Step 2: Make personal center the sole authenticated landing page**

Use `/profile` for root redirect, post-login redirect and unauthorized-route fallback for every role.

**Step 3: Search for retired symbols**

Run: `rg -n "dashboard|首页|DataBoard" frontend/src`

Expected: no application route, navigation or view reference remains.

### Task 3: Display department path in personal center

**Files:**
- Modify: `frontend/src/types/auth.ts`
- Modify: `frontend/src/views/profile/ProfileView.vue`

**Step 1: Bind the explicit field**

Add `departmentPath` to `CurrentUser` and render it for the department row. Render `--` only when the path is absent; do not expose `deptCode` in display text.

**Step 2: Verify TypeScript and production build**

Run: `npm run build`

Expected: exit code 0.

### Task 4: Validate runtime behavior

**Files:**
- Verify only

**Step 1: Build and restart backend**

Run: `E:\ldap\.tools\apache-maven-3.9.6\bin\mvn.cmd clean package`

Run: `E:\ldap\scripts\start-backend-dev.ps1`

**Step 2: Verify APIs**

Log in as administrator and a normal employee through `http://127.0.0.1:5173/api/v1/auth/login`; assert `/api/v1/auth/me` returns `departmentPath` and health is UP.

**Step 3: Verify removal**

Run `rg` for dashboard references and `git diff --check`.
