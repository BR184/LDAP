# Enterprise Workspace Renewal Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the complete authenticated workspace with one maintainable CrownCAD enterprise design system, a real administrator overview, and role-safe responsive business pages.

**Architecture:** The existing Vue and Element Plus implementation is changed in place into the new authority: one token system, one application shell, one page container contract, and a small set of composition-based workspace components. A dedicated dashboard application service aggregates bounded repository queries behind an administrator-only REST endpoint; ordinary employees continue to land on Personal Center and cannot access overview data.

**Tech Stack:** Vue 3, TypeScript, Vite, Pinia, Vue Router, TanStack Vue Query, Element Plus, Vitest, Vue Test Utils, Spring Boot 3, Spring Security, Casbin, MyBatis-Plus, MySQL 8, JUnit 5, Mockito, MockMvc, Flyway.

---

## Execution rules

- Preserve unrelated worktree changes and stage only files for the current task.
- Do not add `v2`, `new_*`, legacy aliases, old-theme toggles, or compatibility branches.
- Change the authoritative implementation and every caller in the same task, then use `rg` to prove the retired symbol is gone.
- Add shared components only after identifying at least three real callers.
- Keep dashboard aggregation bounded behind its dedicated query port; never call `findAll()` to calculate metrics.
- Each task ends with focused tests and a commit before the next task begins.

### Task 1: Establish frontend test infrastructure and design contracts

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Modify: `frontend/vite.config.ts`
- Create: `frontend/src/test/setup.ts`
- Create: `frontend/src/styles/design-system.spec.ts`

**Step 1: Add the failing design contract**

Create a Vitest test that reads the authoritative token file and asserts the required CrownCAD tokens exist while the retired purple value does not:

```ts
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

describe('authenticated workspace design contract', () => {
  const tokens = readFileSync(fileURLToPath(new URL('./variables.scss', import.meta.url)), 'utf8')

  it('uses the CrownCAD enterprise palette without the retired purple accent', () => {
    expect(tokens).toContain('--ui-color-brand:')
    expect(tokens).toContain('--ui-color-highlight:')
    expect(tokens).toContain('--ui-motion-standard:')
    expect(tokens.toLowerCase()).not.toContain('#7c4dff')
  })
})
```

**Step 2: Run the test and verify it fails**

Run: `npm run test -- --run src/styles/design-system.spec.ts`

Expected: FAIL because the test runner/script or new tokens do not exist.

**Step 3: Install the minimum test stack**

Add `vitest`, `@vue/test-utils`, `@pinia/testing`, and `jsdom`; add scripts `test` and `test:watch`; configure `environment: 'jsdom'` and `setupFiles` in Vite.

**Step 4: Run the harness**

Run: `npm run test -- --run src/styles/design-system.spec.ts`

Expected: the runner starts and the design assertions still fail for the intended missing-token reason.

**Step 5: Commit**

```powershell
git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts frontend/src/test/setup.ts frontend/src/styles/design-system.spec.ts
git commit -m "test: establish authenticated workspace UI contracts"
```

### Task 2: Replace the global design tokens and Element Plus theme

**Files:**
- Modify: `frontend/src/styles/variables.scss`
- Modify: `frontend/src/styles/index.scss`
- Test: `frontend/src/styles/design-system.spec.ts`

**Step 1: Define the new authority**

Replace, rather than supplement, the old palette. Required token groups:

```scss
:root {
  --ui-color-brand: #0877b9;
  --ui-color-brand-strong: #075f94;
  --ui-color-highlight: #f2a007;
  --ui-color-text-strong: #172127;
  --ui-color-text: #34434c;
  --ui-color-text-muted: #6f7d85;
  --ui-color-canvas: #f3f5f6;
  --ui-color-surface: #ffffff;
  --ui-color-surface-glass: rgba(255, 255, 255, 0.78);
  --ui-color-border: rgba(23, 33, 39, 0.11);
  --ui-radius-panel: 8px;
  --ui-motion-fast: 160ms;
  --ui-motion-standard: 260ms;
  --ui-ease-standard: cubic-bezier(0.22, 1, 0.36, 1);
}
```

Map Element Plus CSS variables to these tokens. Add global focus, selection, scrollbar, table, form, drawer, dialog, tag, skeleton and reduced-motion rules. Remove `body { min-width: 1280px; }`, `.idm-card`, old purple variables and old generic shadow/radius definitions after every caller is migrated.

**Step 2: Run focused tests**

Run: `npm run test -- --run src/styles/design-system.spec.ts`

Expected: PASS.

**Step 3: Search for retired tokens**

Run: `rg -n -- "--idm-|idm-card|idm-muted|7c4dff|5b8ff9" frontend/src`

Expected: no matches after callers are updated in this task; if callers remain, update them now rather than adding aliases.

**Step 4: Commit**

```powershell
git add frontend/src/styles/variables.scss frontend/src/styles/index.scss frontend/src/styles/design-system.spec.ts
git commit -m "feat: establish CrownCAD enterprise design system"
```

### Task 3: Make access and navigation definitions explicit

**Files:**
- Create: `frontend/src/constants/access.ts`
- Modify: `frontend/src/constants/navigation.ts`
- Modify: `frontend/src/stores/auth.ts`
- Modify: `frontend/src/stores/menu.ts`
- Modify: `frontend/src/router/index.ts`
- Create: `frontend/src/stores/access.spec.ts`

**Step 1: Write failing role-boundary tests**

Cover these invariants:

```ts
expect(defaultEntryFor(['NORMAL_USER'])).toBe('/profile')
expect(defaultEntryFor(['ADMIN'])).toBe('/dashboard')
expect(canOpenDashboard(['NORMAL_USER'])).toBe(false)
expect(canOpenDashboard(['SUPER_ADMIN'])).toBe(true)
```

**Step 2: Run and verify failure**

Run: `npm run test -- --run src/stores/access.spec.ts`

Expected: FAIL because the pure access functions do not exist.

**Step 3: Implement one access definition**

Move repeated administrator role codes and route decisions into pure, typed functions in `constants/access.ts`. Replace the duplicated flat/group navigation definitions with one `NAVIGATION_SCHEMA_VERSION` and one validated navigation tree. Give records stable IDs independent of titles, for example `workspace.dashboard`, `identity.users`, and `account.profile`; validate unique IDs and paths at module initialization. Derive lookups and sidebar groups from the tree. Use the backend menu tree as the visibility authority and remove the frontend administrator override that bypasses menu grants. Update stores, router and layout callers directly; remove duplicate role sets and title-based decisions.

**Step 4: Verify**

Run: `npm run test -- --run src/stores/access.spec.ts`

Expected: PASS.

Run: `rg -n "new_|_v2|ADMIN_ROLE_CODES" frontend/src`

Expected: no replacement-wrapper names and only the single authoritative role definition.

**Step 5: Commit**

```powershell
git add frontend/src/constants/access.ts frontend/src/constants/navigation.ts frontend/src/stores/auth.ts frontend/src/stores/menu.ts frontend/src/router/index.ts frontend/src/stores/access.spec.ts
git commit -m "refactor: centralize workspace access definitions"
```

### Task 4: Rebuild the application shell and page container

**Files:**
- Modify: `frontend/src/layout/AdminLayout.vue`
- Create: `frontend/src/layout/components/SidebarNavigation.vue`
- Modify: `frontend/src/components/PageContainer.vue`
- Modify: `frontend/src/stores/app.ts`
- Create: `frontend/src/layout/AdminLayout.spec.ts`
- Create: `frontend/src/components/PageContainer.spec.ts`

**Step 1: Write failing component tests**

Assert that an administrator sees Dashboard, an ordinary employee does not, Personal Center is available, the top bar contains one logout control, and the page container exposes title, description, meta and action slots.

**Step 2: Run and verify failure**

Run: `npm run test -- --run src/layout/AdminLayout.spec.ts src/components/PageContainer.spec.ts`

Expected: FAIL against the old shell contract.

**Step 3: Rewrite existing components in place**

- Use CrownCAD Logo/Icon assets already in `frontend/public`.
- Build the graphite/white shell, glass top bar, new active navigation treatment and overlay sidebar behavior. Desktop and mobile render the same `SidebarNavigation` DOM and authoritative tree.
- Keep navigation content configuration-driven and route-safe.
- Add a compact mobile header and close the overlay on route change or Escape.
- Extend `PageContainer` slots directly; do not create `PageContainerV2`.

**Step 4: Verify**

Run: `npm run test -- --run src/layout/AdminLayout.spec.ts src/components/PageContainer.spec.ts`

Expected: PASS.

Run: `npm run check`

Expected: exit code 0.

**Step 5: Commit**

```powershell
git add frontend/src/layout/AdminLayout.vue frontend/src/layout/components/SidebarNavigation.vue frontend/src/layout/AdminLayout.spec.ts frontend/src/components/PageContainer.vue frontend/src/components/PageContainer.spec.ts frontend/src/stores/app.ts
git commit -m "feat: rebuild authenticated application shell"
```

### Task 5: Add bounded dashboard aggregation to the backend

**Files:**
- Create: `src/main/java/com/company/idm/application/dashboard/DashboardOverview.java`
- Create: `src/main/java/com/company/idm/application/dashboard/DashboardOverviewQuery.java`
- Create: `src/main/java/com/company/idm/application/dashboard/DashboardOverviewPolicy.java`
- Create: `src/main/java/com/company/idm/application/dashboard/DashboardApplicationService.java`
- Create: `src/main/java/com/company/idm/interfaces/dashboard/DashboardController.java`
- Create: `src/main/java/com/company/idm/interfaces/dashboard/DashboardOverviewResponse.java`
- Create: `src/main/java/com/company/idm/infrastructure/persistence/mapper/DashboardOverviewMapper.java`
- Create: `src/main/java/com/company/idm/infrastructure/persistence/query/MybatisDashboardOverviewQuery.java`
- Create: `src/main/java/com/company/idm/infrastructure/config/DashboardOverviewConfiguration.java`
- Create: `src/main/java/com/company/idm/infrastructure/config/DashboardOverviewProperties.java`
- Create: `src/main/java/com/company/idm/infrastructure/config/ApplicationTimeConfiguration.java`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/resources/db/migration/V33__dashboard_overview.sql`
- Create: `src/test/java/com/company/idm/application/dashboard/DashboardApplicationServiceTest.java`
- Create: `src/test/java/com/company/idm/interfaces/dashboard/DashboardControllerTest.java`
- Create: `src/test/java/com/company/idm/infrastructure/persistence/query/MybatisDashboardOverviewQueryIT.java`

**Step 1: Write the failing aggregation test**

Mock the dedicated query port, inject a fixed `Clock`, and assert the application service passes the exact attention window and bounded recent-job limit. Parameterize all health states.

```java
verify(dashboardOverviewQuery).load(
    LocalDateTime.of(2026, 7, 21, 12, 0),
    policy.recentSyncJobLimit()
);
```

**Step 2: Write the failing authorization test**

Use the existing MockMvc security support. Assert allowed Casbin access returns 200 and denied access returns 403 for `GET /api/v1/dashboard/overview`.

**Step 3: Run and verify failure**

Run: `mvn -Dtest=DashboardApplicationServiceTest,DashboardControllerTest test`

Expected: FAIL because the dashboard types and endpoint do not exist.

**Step 4: Implement the dedicated read boundary**

Define one `DashboardOverviewQuery.load(LocalDateTime attentionSince, int recentSyncJobLimit)` port. Its MyBatis implementation executes exactly three bounded SQL statements: resource status counts, synchronization summary and recent jobs. Do not add dashboard methods to user/department/role repositories and do not load domain entity collections.

Define `DashboardOverviewPolicy` as an immutable application value with validated bounds. Map `app.dashboard.sync-attention-window-hours` (1–168) and `recent-sync-job-limit` (1–20) from configuration into that value. Inject an explicit `Clock`.

**Step 5: Add the stable API contract and authorization**

The response includes `generatedAt`, account/department/role status counts, synchronization health and bounded recent jobs. It excludes request/result JSON, errors and user/organization details. `health` is one of `NO_DATA`, `UNKNOWN`, `HEALTHY`, `PENDING`, `RUNNING`, `RECENT_ANOMALY`.

**Step 6: Add permission, menu and measured-query indexes**

Migration V33 adds stable permission `DASHBOARD_OVERVIEW_READ`, stable menu `DASHBOARD_OVERVIEW`, and binds both only to `ADMIN` and `SUPER_ADMIN` without hard-coded database IDs. Add only indexes exercised by the queries: `sys_user(deleted, status)` and `sys_sync_job(status, end_time)`. Recent jobs use the primary key descending path.

**Step 7: Verify unit, authorization and persistence behavior**

Run: `mvn -Dtest=DashboardApplicationServiceTest,DashboardControllerTest,MybatisDashboardOverviewQueryIT test`

Expected: PASS.

**Step 8: Commit**

```powershell
git add src/main/java/com/company/idm/application/dashboard src/main/java/com/company/idm/interfaces/dashboard src/main/java/com/company/idm/infrastructure/persistence/mapper/DashboardOverviewMapper.java src/main/java/com/company/idm/infrastructure/persistence/query/MybatisDashboardOverviewQuery.java src/main/java/com/company/idm/infrastructure/config/DashboardOverviewConfiguration.java src/main/java/com/company/idm/infrastructure/config/DashboardOverviewProperties.java src/main/java/com/company/idm/infrastructure/config/ApplicationTimeConfiguration.java src/main/resources/application.yml src/main/resources/db/migration/V33__dashboard_overview.sql src/test/java/com/company/idm/application/dashboard src/test/java/com/company/idm/interfaces/dashboard src/test/java/com/company/idm/infrastructure/persistence/query
git commit -m "feat: add bounded administrator dashboard overview"
```

### Task 6: Replace the placeholder dashboard with real data

**Files:**
- Create: `frontend/src/types/dashboard.ts`
- Create: `frontend/src/api/modules/dashboard.ts`
- Modify: `frontend/src/views/dashboard/DashboardView.vue`
- Create: `frontend/src/views/dashboard/DashboardView.spec.ts`
- Modify: `frontend/src/views/system/NotFoundView.vue`

**Step 1: Write failing state tests**

Test real data, loading, empty recent jobs, API failure/retry and permission-filtered actions. Assert the old text `优先进入核心业务页继续接入真实数据` never renders.

**Step 2: Run and verify failure**

Run: `npm run test -- --run src/views/dashboard/DashboardView.spec.ts`

Expected: FAIL against the placeholder dashboard.

**Step 3: Implement the dashboard**

- Fetch only `/v1/dashboard/overview` through Vue Query. Accept the network payload as `unknown` and parse non-negative counts, health enums, timestamps and the bounded recent-job array before exposing `DashboardOverview` to the view.
- Render system status, four real metrics, recent jobs, actionable failures and permission-filtered actions in an asymmetric grid.
- Show a timestamp and manual refresh; do not add polling until measured operational need exists.
- Route Not Found back to `authStore.defaultEntryPath`, not a hard-coded dashboard.
- Delete the old `flatNavigationItems` shortcut rendering and its CSS.

**Step 4: Verify**

Run: `npm run test -- --run src/views/dashboard/DashboardView.spec.ts`

Expected: PASS.

Run: `rg -n "继续接入真实数据|dashboard-shortcuts" frontend/src`

Expected: no matches.

**Step 5: Commit**

```powershell
git add frontend/src/types/dashboard.ts frontend/src/api/modules/dashboard.ts frontend/src/views/dashboard/DashboardView.vue frontend/src/views/dashboard/DashboardView.spec.ts frontend/src/views/system/NotFoundView.vue
git commit -m "feat: deliver real administrator overview"
```

### Task 7: Add repeated workspace and status primitives

**Files:**
- Create: `frontend/src/components/workspace/WorkspacePanel.vue`
- Create: `frontend/src/components/workspace/DataToolbar.vue`
- Create: `frontend/src/components/StatusBadge.vue`
- Create: `frontend/src/constants/status-presentation.ts`
- Create: `frontend/src/components/workspace/WorkspacePrimitives.spec.ts`

**Step 1: Write failing slot and accessibility tests**

Assert semantic region labels, header/action slots, loading/error/empty states and toolbar wrapping. Assert status presentation is keyed by stable domain/code pairs and unknown codes fall back to readable raw values without throwing.

**Step 2: Run and verify failure**

Run: `npm run test -- --run src/components/workspace/WorkspacePrimitives.spec.ts`

Expected: FAIL because the primitives do not exist.

**Step 3: Implement composition-only primitives**

Keep components stateless. Pass data and actions explicitly through props/slots; do not add an event bus or shared mutable store. Centralize repeated status labels, icons and tones in the versioned status resource; migrate callers directly instead of keeping page-local mapping functions.

**Step 4: Verify and commit**

Run: `npm run test -- --run src/components/workspace/WorkspacePrimitives.spec.ts`

Expected: PASS.

```powershell
git add frontend/src/components/workspace frontend/src/components/StatusBadge.vue frontend/src/constants/status-presentation.ts
git commit -m "feat: add composable workspace primitives"
```

### Task 8: Replace full-list APIs with one validated pagination model

**Files:**
- Create: `src/main/java/com/company/idm/common/model/PageRequest.java`
- Create: `src/main/java/com/company/idm/common/model/PageSlice.java`
- Create: `src/main/java/com/company/idm/common/api/PageResponse.java`
- Modify: `src/main/java/com/company/idm/domain/user/UserRepository.java`
- Modify: `src/main/java/com/company/idm/domain/rbac/RoleRepository.java`
- Modify: `src/main/java/com/company/idm/domain/sync/SyncJobRepository.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/repository/MybatisUserRepository.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/repository/MybatisRoleRepository.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/repository/MybatisSyncJobRepository.java`
- Modify: `src/main/java/com/company/idm/application/user/UserApplicationService.java`
- Modify: `src/main/java/com/company/idm/application/rbac/RbacApplicationService.java`
- Modify: `src/main/java/com/company/idm/application/sync/SyncApplicationService.java`
- Modify: `src/main/java/com/company/idm/interfaces/user/UserController.java`
- Modify: `src/main/java/com/company/idm/interfaces/user/BatchDeleteUsersRequest.java`
- Modify: `src/main/java/com/company/idm/interfaces/role/RoleController.java`
- Modify: `src/main/java/com/company/idm/interfaces/sync/SyncController.java`
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/types/user.ts`
- Modify: `frontend/src/types/role.ts`
- Modify: `frontend/src/types/sync.ts`
- Modify: `frontend/src/api/modules/user.ts`
- Modify: `frontend/src/api/modules/role.ts`
- Modify: `frontend/src/api/modules/sync.ts`
- Create: `src/test/java/com/company/idm/common/model/PageRequestTest.java`
- Create: `src/test/java/com/company/idm/application/user/UserPaginationTest.java`
- Create: `src/test/java/com/company/idm/application/rbac/RolePaginationTest.java`
- Create: `src/test/java/com/company/idm/application/sync/SyncJobPaginationTest.java`

**Step 1: Write failing boundary tests**

Test page numbering, total calculation, empty pages, maximum page size and invalid negative/oversized requests. Test that each application service invokes a paged repository method and never calls `findAll()` for list endpoints.

**Step 2: Run and verify failure**

Run: `mvn -Dtest=PageRequestTest,UserPaginationTest,RolePaginationTest,SyncJobPaginationTest test`

Expected: FAIL because the pagination authority does not exist.

**Step 3: Implement the immutable pagination authority**

First record the contract audit: repository search currently identifies only this frontend as a caller of the three list endpoints and no published external contract. If a real external consumer is discovered, stop and obtain explicit compatibility scope, deadline and removal conditions before changing the endpoint. Otherwise, treat the endpoints as internal and proceed directly.

Use one validated `PageRequest` and immutable `PageSlice<T>` internally; map it to one `PageResponse<T>` at the interface boundary. The REST list endpoints return `{ items, page, pageSize, total }`. Update all frontend types and API callers in the same task; do not keep the old array response or add response-shape detection.

**Step 4: Remove unbounded batch behavior**

Batch user deletion must require explicit IDs and validate a documented maximum batch size. Delete the current empty-ID branch that resolves every matching user synchronously and remove its filter fields from command/request/frontend types. If whole-result deletion is required later, it must be a separately designed asynchronous job rather than a fallback branch.

**Step 5: Implement database pagination**

Use MyBatis-Plus/MySQL limit and count queries with the existing filters. Keep sort fields fixed by code and never accept an unchecked column name. Add query indexes only after `EXPLAIN ANALYZE` demonstrates the path used by the new endpoints.

**Step 6: Verify old list shapes are gone**

Run: `rg -n "Promise<.*\[\]>|request\.get<never, (UserItem|RoleItem|SyncJob)\[\]>|\.slice\(start" frontend/src/api frontend/src/views/user frontend/src/views/role frontend/src/views/sync`

Expected: no old full-list API or browser-pagination matches.

**Step 7: Run tests and commit**

Run: `mvn -Dtest=PageRequestTest,UserPaginationTest,RolePaginationTest,SyncJobPaginationTest test`

Expected: PASS.

```powershell
git add src/main/java/com/company/idm/common src/main/java/com/company/idm/domain/user/UserRepository.java src/main/java/com/company/idm/domain/rbac/RoleRepository.java src/main/java/com/company/idm/domain/sync/SyncJobRepository.java src/main/java/com/company/idm/infrastructure/persistence/repository src/main/java/com/company/idm/application src/main/java/com/company/idm/interfaces frontend/src/types frontend/src/api src/test/java/com/company/idm/common src/test/java/com/company/idm/application
git commit -m "refactor: replace full-list APIs with bounded pagination"
```

### Task 9: Migrate all list workspaces

**Files:**
- Modify: `frontend/src/views/user/UserListView.vue`
- Modify: `frontend/src/views/role/RoleListView.vue`
- Modify: `frontend/src/views/sync/SyncJobListView.vue`
- Create: `frontend/src/views/user/UserListView.spec.ts`
- Create: `frontend/src/views/role/RoleListView.spec.ts`
- Create: `frontend/src/views/sync/SyncJobListView.spec.ts`

**Step 1: Write behavior-preservation tests**

Cover query/reset, loading, selection, batch actions, pagination, row actions, status display and failure messages before changing markup.

**Step 2: Run focused tests and record the baseline**

Run: `npm run test -- --run src/views/user/UserListView.spec.ts src/views/role/RoleListView.spec.ts src/views/sync/SyncJobListView.spec.ts`

Expected: tests pass for existing behavior or fail only where the test exposes an existing defect that must be fixed in this task.

**Step 3: Migrate all three views**

Use `DataToolbar` and `WorkspacePanel`, keep high-density tables and fixed action columns, add explicit skeleton/error/empty states, and delete repeated `view-toolbar`, card and footer CSS after the last caller moves.

**Step 4: Verify retired CSS is gone**

Run: `rg -n "view-toolbar|idm-card" frontend/src/views/user frontend/src/views/role frontend/src/views/sync`

Expected: no matches.

**Step 5: Run and commit**

Run: `npm run test -- --run src/views/user/UserListView.spec.ts src/views/role/RoleListView.spec.ts src/views/sync/SyncJobListView.spec.ts`

Expected: PASS.

```powershell
git add frontend/src/views/user/UserListView.vue frontend/src/views/user/UserListView.spec.ts frontend/src/views/role/RoleListView.vue frontend/src/views/role/RoleListView.spec.ts frontend/src/views/sync/SyncJobListView.vue frontend/src/views/sync/SyncJobListView.spec.ts
git commit -m "feat: renew list management workspaces"
```

### Task 10: Migrate tree and relationship workspaces

**Files:**
- Modify: `frontend/src/views/department/DepartmentTreeView.vue`
- Modify: `frontend/src/views/menu/MenuTreeView.vue`
- Modify: `frontend/src/views/permission/PermissionTreeView.vue`
- Create: `frontend/src/views/department/DepartmentTreeView.spec.ts`
- Create: `frontend/src/views/menu/MenuTreeView.spec.ts`

**Step 1: Write selection and operation tests**

Cover tree loading, active-node state, empty tree, detail switching, create/edit/delete controls and narrow-layout disclosure.

**Step 2: Run and verify the behavior baseline**

Run: `npm run test -- --run src/views/department/DepartmentTreeView.spec.ts src/views/menu/MenuTreeView.spec.ts`

**Step 3: Migrate the two page-local split layouts**

Use a narrow tree rail plus expansive detail canvas on desktop and an explicit tree drawer/disclosure on narrow screens. Preserve business behavior and delete the fixed `320px` layout rules. Do not extract a shared split component while there are only two callers.

**Step 4: Verify and commit**

Run: `npm run test -- --run src/views/department/DepartmentTreeView.spec.ts src/views/menu/MenuTreeView.spec.ts`

Expected: PASS.

```powershell
git add frontend/src/views/department frontend/src/views/menu frontend/src/views/permission
git commit -m "feat: renew hierarchical management workspaces"
```

### Task 11: Migrate configuration, import and account workspaces

**Files:**
- Modify: `frontend/src/views/ldap/LdapControlView.vue`
- Modify: `frontend/src/views/system/SystemMailConfigView.vue`
- Modify: `frontend/src/views/system/SystemImportView.vue`
- Modify: `frontend/src/views/profile/ProfileView.vue`
- Create: `frontend/src/views/ldap/LdapControlView.spec.ts`
- Create: `frontend/src/views/system/SystemMailConfigView.spec.ts`
- Create: `frontend/src/views/profile/ProfileView.spec.ts`

**Step 1: Write critical-flow tests**

Cover LDAP precheck, mail configuration/test, import mode and result states, profile rendering, password verification, confirmation and error behavior.

**Step 2: Run the baseline**

Run: `npm run test -- --run src/views/ldap/LdapControlView.spec.ts src/views/system/SystemMailConfigView.spec.ts src/views/profile/ProfileView.spec.ts`

**Step 3: Recompose each page around its task**

- LDAP: connection state, target configuration, precheck result and next action.
- Mail: service status, configuration sections, test result and safe credential handling.
- Import: source/input, validation/review, execution and result timeline.
- Profile: identity summary and account security with no administrator metrics.

Use the same tokens and workspace primitives; keep single-page-specific layouts local.

**Step 4: Verify and commit**

Run: `npm run test -- --run src/views/ldap/LdapControlView.spec.ts src/views/system/SystemMailConfigView.spec.ts src/views/profile/ProfileView.spec.ts`

Expected: PASS.

```powershell
git add frontend/src/views/ldap frontend/src/views/system frontend/src/views/profile
git commit -m "feat: renew configuration and account workspaces"
```

### Task 12: Standardize drawers, forms and dialogs in place

**Files:**
- Modify: `frontend/src/views/user/components/UserDetailDrawer.vue`
- Modify: `frontend/src/views/user/components/UserFormDrawer.vue`
- Modify: `frontend/src/views/user/components/UserRoleDrawer.vue`
- Modify: `frontend/src/views/department/components/DepartmentFormDrawer.vue`
- Modify: `frontend/src/views/menu/components/MenuFormDrawer.vue`
- Modify: `frontend/src/views/role/components/RoleFormDrawer.vue`
- Modify: `frontend/src/views/role/components/RoleMenuDrawer.vue`
- Modify: `frontend/src/views/role/components/RolePermissionDrawer.vue`
- Create: `frontend/src/views/user/components/UserFormDrawer.spec.ts`
- Create: `frontend/src/views/role/components/RolePermissionDrawer.spec.ts`

**Step 1: Write validation and submission tests**

Cover open/reset, prefill, client validation, submit payload, pending state, server failure, cancel and dangerous confirmation behavior.

**Step 2: Migrate existing components directly**

Use the global drawer/dialog contract, fixed footer actions, consistent labels and error placement. Do not add replacement wrappers or retain duplicate footer CSS.

**Step 3: Verify all component tests**

Run: `npm run test -- --run src/views/user/components/UserFormDrawer.spec.ts src/views/role/components/RolePermissionDrawer.spec.ts`

Expected: PASS.

**Step 4: Commit**

```powershell
git add frontend/src/views/user/components frontend/src/views/department/components frontend/src/views/menu/components frontend/src/views/role/components
git commit -m "feat: standardize management forms and drawers"
```

### Task 13: Validate responsive behavior, motion and accessibility

**Files:**
- Modify: `frontend/src/styles/index.scss`
- Modify: relevant view files only where browser verification finds a concrete issue
- Create: `frontend/tests/e2e/workspace-roles.spec.ts`
- Create: `frontend/tests/e2e/workspace-responsive.spec.ts`
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `frontend/playwright.config.ts`

**Step 1: Add failing browser acceptance tests**

Cover administrator and employee default routes, hidden dashboard access, desktop shell, overlay sidebar, horizontal table scroll, drawer focus/escape and reduced-motion mode.

**Step 2: Run and verify failure**

Run: `npm run test:e2e -- --project=chromium`

Expected: FAIL until the full browser behavior and authenticated fixtures are configured.

**Step 3: Fix concrete browser findings**

Test 1440, 1280, 1024, 768 and 390 CSS-pixel widths. Change only the authoritative layout/page rules that cause verified overflow, truncation, focus or motion issues.

**Step 4: Verify**

Run: `npm run test:e2e -- --project=chromium`

Expected: PASS with screenshots for dashboard, profile, user list, department tree, import and representative drawers.

**Step 5: Commit**

```powershell
git add frontend/package.json frontend/package-lock.json frontend/playwright.config.ts frontend/tests/e2e frontend/src
git commit -m "test: validate responsive authenticated workspace"
```

### Task 14: Benchmark, delete retired paths and update documentation

**Files:**
- Create: `scripts/benchmark-dashboard-overview.ps1`
- Create: `docs/benchmarks/dashboard-overview.md`
- Modify: `docs/project-design.md`
- Modify: `frontend/README.md`
- Delete: `frontend/src/views/permission/PermissionTreeView.vue`
- Delete: `frontend/src/assets/hero.png`
- Delete: `frontend/src/assets/vite.svg`
- Delete: `frontend/src/assets/vue.svg`
- Delete: `frontend/public/favicon.svg`
- Delete: `frontend/public/icons.svg`
- Delete: `frontend/public/login-hero.png`
- Modify: files identified by retired-symbol search

**Step 1: Establish the fixed-load benchmark**

The script accepts base URL, token and sample count; records warmup, p50, p95, maximum response time, response bytes and failures. The benchmark document records dataset sizes, database version, hardware/container limits, SQL count and `EXPLAIN ANALYZE` output. Do not invent a pass threshold before recording the baseline.

**Step 2: Run the benchmark**

Run: `powershell -File scripts/benchmark-dashboard-overview.ps1 -BaseUrl http://127.0.0.1:8081 -Token <admin-token> -Samples 100`

Expected: 100 authorized responses with bounded payloads and recorded measurements.

**Step 3: Remove all retired implementation**

Run searches for old `--idm-*` variables, purple colors, `.idm-card`, old dashboard shortcut text/classes, duplicated role sets, duplicated navigation arrays, fixed 1280 body width, retired toolbar/footer classes, page-local status mapping functions, and any `v2`/`new_` workaround names. Confirm the listed placeholder page and legacy assets have no references, then delete them. Delete or migrate every old-symbol match; do not add aliases.

**Step 4: Run the complete verification suite**

Run: `npm run test -- --run`

Run: `npm run check`

Run: `npm run build`

Run: `mvn test`

Run: `npm run test:e2e -- --project=chromium`

Expected: all commands pass. The Vite bundle-size warning may be documented separately but must not hide build failures.

**Step 5: Update documentation**

Document the dashboard endpoint, role boundary, design-token authority, workspace component contracts, responsive behavior, reduced-motion behavior, fixed-load results and removal of the old UI path.

**Step 6: Commit**

```powershell
git add scripts/benchmark-dashboard-overview.ps1 docs/benchmarks/dashboard-overview.md docs/project-design.md frontend/README.md frontend/src src/main src/test
git commit -m "docs: finalize enterprise workspace renewal"
```

## Final acceptance checklist

- Ordinary employees cannot see or access dashboard UI/API data.
- Administrators receive real bounded metrics and actionable task data.
- Every authenticated page, table, form, tree, drawer and dialog uses the same authoritative design system.
- No blue-purple AI aesthetic, fake chart, old-theme switch, compatibility wrapper or parallel internal model remains.
- Motion is non-linear but bounded, purposeful and disabled when reduced motion is requested.
- Fixed-load measurements and all automated/browser tests are recorded and passing.
