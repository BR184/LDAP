# Module Permission Suites and Built-In Role Safety Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace flat role permission presets with ten module-scoped STANDARD/ADMIN suites, make management pages capability-aware, split role-group read access from management, protect built-in role structure, and repair the role-group rail layout.

**Architecture:** Keep granular permissions as the only authorization source of truth. A versioned JSON catalog declares explicit module and tier metadata, the backend validates and exposes the resolved bundles without removing existing response fields, and the Vue client maps those bundles to a three-state module selector while retaining the granular tree. A new session-only capabilities endpoint drives UI availability; backend annotations and services remain the security boundary.

**Tech Stack:** Java 17, Spring Boot, Spring Security method authorization, MyBatis, Flyway, JUnit 5, Mockito, Vue 3, TypeScript, Pinia, Element Plus, Vitest, Docker Compose.

---

### Task 1: Add Role-Group Read Permission and Preserve Existing Managers

**Files:**
- Create: `src/main/resources/db/migration/V51__module_permission_suites_and_role_group_read.sql`
- Modify: `src/main/java/com/company/idm/interfaces/rolegroup/RoleGroupController.java`
- Modify: `src/main/java/com/company/idm/interfaces/rolegroup/RoleSupplyTokenController.java`
- Test: `src/test/java/com/company/idm/interfaces/rolegroup/RoleGroupControllerAuthorizationTest.java`

**Step 1: Write the failing authorization test**

Create controller authorization tests that inspect method/class `@PreAuthorize` declarations and assert:

- GET operations accept `ROLE_GROUP_READ` or `ROLE_GROUP_MANAGE`.
- write operations require `ROLE_GROUP_MANAGE`.
- member assignment requires both management and `ROLE_GROUP_USER_ASSIGN`.
- role-group token management requires `ROLE_GROUP_MANAGE` in addition to session credentials.

**Step 2: Run the focused test and verify it fails**

Run:

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd -Dtest=RoleGroupControllerAuthorizationTest test
```

Expected: FAIL because the controller currently has one class-level `ROLE_GROUP_MANAGE` expression and token endpoints are session-only.

**Step 3: Add the migration**

Insert `ROLE_GROUP_READ` with a stable Chinese name and API metadata. Backfill it to every role currently assigned `ROLE_GROUP_MANAGE`:

```sql
INSERT INTO sys_permission (...)
SELECT 'ROLE_GROUP_READ', '委派角色组查询', 'API', '/api/v1/role-groups', 'GET', ...
WHERE NOT EXISTS (...);

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT manager_binding.role_id, read_permission.id, 'system'
FROM sys_role_permission manager_binding
JOIN sys_permission manager_permission
  ON manager_permission.id = manager_binding.permission_id
 AND manager_permission.permission_code = 'ROLE_GROUP_MANAGE'
JOIN sys_permission read_permission
  ON read_permission.permission_code = 'ROLE_GROUP_READ'
LEFT JOIN sys_role_permission existing
  ON existing.role_id = manager_binding.role_id
 AND existing.permission_id = read_permission.id
WHERE existing.role_id IS NULL;
```

**Step 4: Split controller authorization by operation**

Remove the role-group controller class-level authorization. Apply read-or-manage expressions to GET methods and management expressions to mutation methods. Preserve application-service OWNER/MANAGER/platform-admin checks. Add management authorization to role-group token management endpoints without changing request or response fields.

**Step 5: Run focused tests**

Run the command from Step 2.

Expected: PASS.

**Step 6: Commit**

```powershell
git add src/main/resources/db/migration/V51__module_permission_suites_and_role_group_read.sql src/main/java/com/company/idm/interfaces/rolegroup src/test/java/com/company/idm/interfaces/rolegroup/RoleGroupControllerAuthorizationTest.java
git commit -m "feat: split role group read and management access"
```

### Task 2: Replace the Flat Bundle Catalog with a Validated Module Catalog

**Files:**
- Modify: `src/main/resources/security/role-permission-bundles.json`
- Modify: `src/main/java/com/company/idm/application/rbac/RolePermissionBundleCatalog.java`
- Modify: `src/main/java/com/company/idm/application/rbac/RolePermissionBundle.java`
- Modify: `src/main/java/com/company/idm/interfaces/permission/PermissionController.java`
- Test: `src/test/java/com/company/idm/application/rbac/RolePermissionBundleCatalogTest.java`

**Step 1: Replace catalog tests with the target model**

Test these invariants:

- exactly ten category IDs are loaded;
- every category has exactly one `STANDARD` and one `ADMIN` bundle;
- every ADMIN permission set strictly contains its STANDARD set;
- duplicate category IDs, duplicate bundle IDs, unknown tier values, missing permissions and invalid supersets fail startup validation;
- resolved permission IDs follow the explicit permission-code order;
- the bundle response still contains `id`, `name`, `sort`, `permissionIds`, and `permissionCodes`.

**Step 2: Run the focused catalog test and verify it fails**

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd -Dtest=RolePermissionBundleCatalogTest test
```

Expected: FAIL because the current document contains only a flat `bundles` array.

**Step 3: Define the version 2 JSON resource**

Add ten categories with stable IDs:

```text
USER_MANAGEMENT
DEPARTMENT_MANAGEMENT
ROLE_MANAGEMENT
PERMISSION_ASSIGNMENT
ROLE_GROUP_MANAGEMENT
MENU_MANAGEMENT
FILE_IMPORT
SYNC_OPERATIONS
LDAP_CONTROL
MAIL_CONFIGURATION
```

Each category contains explicit `STANDARD` and `ADMIN` bundle permission lists from the approved design. Do not derive permissions from prefixes or HTTP methods.

**Step 4: Implement catalog records and validation**

Introduce a tier enum and category definition record. Flatten validated category bundles into `RolePermissionBundle` responses containing the original fields plus:

```java
String categoryId,
String categoryName,
String categoryDescription,
int categorySort,
RolePermissionBundleTier tier
```

Validate ADMIN as a strict superset of STANDARD before resolving database IDs.

**Step 5: Preserve the API contract while adding metadata**

Extend `RolePermissionBundleResponse` by appending the new fields. Do not rename, remove or change the types of existing fields.

**Step 6: Run focused tests**

Run the command from Step 2.

Expected: PASS with ten categories and twenty bundles.

**Step 7: Commit**

```powershell
git add src/main/resources/security/role-permission-bundles.json src/main/java/com/company/idm/application/rbac src/main/java/com/company/idm/interfaces/permission/PermissionController.java src/test/java/com/company/idm/application/rbac/RolePermissionBundleCatalogTest.java
git commit -m "feat: add module-scoped permission suites"
```

### Task 3: Expose Current Session Capabilities Without Changing `/auth/me`

**Files:**
- Create: `src/main/java/com/company/idm/interfaces/auth/CurrentCapabilitiesResponse.java`
- Modify: `src/main/java/com/company/idm/interfaces/auth/AuthController.java`
- Test: `src/test/java/com/company/idm/interfaces/auth/AuthControllerCapabilitiesTest.java`

**Step 1: Write the failing controller test**

Instantiate `AuthController` with a mocked `EffectivePermissionService`. Verify that a session principal returns sorted effective permission codes in a response shaped as:

```json
{
  "permissionCodes": ["AUTH_ME", "USER_READ"]
}
```

Also assert that `CurrentUserResponse` remains unchanged by leaving `CurrentUserResponseTest` intact.

**Step 2: Run the focused tests and verify failure**

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd -Dtest=AuthControllerCapabilitiesTest,CurrentUserResponseTest test
```

Expected: FAIL because `/capabilities` and its response do not exist.

**Step 3: Implement the session-only endpoint**

Inject `EffectivePermissionService` and add:

```java
@GetMapping("/capabilities")
@PreAuthorize("@credentialAccessService.isSession(authentication)")
public ApiResponse<CurrentCapabilitiesResponse> capabilities(
    @AuthenticationPrincipal AuthenticatedUser principal
) { ... }
```

Sort the returned codes for deterministic output. Do not alter login or `/me` response records.

**Step 4: Run focused tests**

Run the command from Step 2.

Expected: PASS.

**Step 5: Commit**

```powershell
git add src/main/java/com/company/idm/interfaces/auth src/test/java/com/company/idm/interfaces/auth
git commit -m "feat: expose current session capabilities"
```

### Task 4: Enforce Built-In Role Structural Locks in the Backend

**Files:**
- Modify: `src/main/java/com/company/idm/application/rbac/RbacApplicationService.java`
- Modify: `src/main/java/com/company/idm/application/rolegroup/RoleGovernanceApplicationService.java`
- Test: `src/test/java/com/company/idm/application/rbac/RbacApplicationServiceRoleProtectionTest.java`
- Test: `src/test/java/com/company/idm/application/rolegroup/RoleGovernanceApplicationServiceTest.java`

**Step 1: Write failing service tests**

Cover built-in roles for:

- permission-level change rejected with `BUILT_IN_ROLE_PERMISSION_LEVEL_LOCKED`;
- status change rejected with `BUILT_IN_ROLE_STATUS_LOCKED`;
- single and batch delete rejected with `BUILT_IN_ROLE_DELETE_FORBIDDEN`;
- scope change rejected with `BUILT_IN_ROLE_SCOPE_LOCKED` before group or permission validation;
- name/remark update with unchanged permission level remains allowed;
- custom roles retain existing behavior.

**Step 2: Run focused tests and verify failure**

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd -Dtest=RbacApplicationServiceRoleProtectionTest,RoleGovernanceApplicationServiceTest test
```

Expected: FAIL because only the super-admin role has partial delete protection and scope changes ignore `builtIn`.

**Step 3: Add explicit guards at mutation boundaries**

Use `role.getBuiltIn() == 1` as the sole structural-lock condition. Keep permission assignment, display-name changes and remark changes available. Do not add a lock column, feature flag or compatibility branch.

**Step 4: Run focused tests**

Run the command from Step 2.

Expected: PASS.

**Step 5: Commit**

```powershell
git add src/main/java/com/company/idm/application/rbac/RbacApplicationService.java src/main/java/com/company/idm/application/rolegroup/RoleGovernanceApplicationService.java src/test/java/com/company/idm/application
git commit -m "fix: protect built-in role structure"
```

### Task 5: Add Frontend Capability State and Testable Suite Selection Logic

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `frontend/src/types/capability.ts`
- Modify: `frontend/src/types/permission.ts`
- Modify: `frontend/src/api/modules/auth.ts`
- Modify: `frontend/src/stores/auth.ts`
- Create: `frontend/src/views/role/components/permissionSuiteSelection.ts`
- Create: `frontend/src/views/role/components/permissionSuiteSelection.spec.ts`

**Step 1: Add Vitest and a failing pure-logic test**

Add `vitest` and an `npm run test` script. Test:

- ADMIN is detected before STANDARD;
- partial permissions produce `CUSTOM`;
- selecting STANDARD removes admin-only permissions;
- selecting ADMIN adds the complete set;
- clearing a module retains shared permissions required by another selected module;
- unrelated granular permissions survive every suite change.

**Step 2: Run the test and verify failure**

```powershell
Set-Location frontend
npm run test -- --run
```

Expected: FAIL because the helper and new bundle metadata do not exist.

**Step 3: Add capability API types and store behavior**

Add `fetchCurrentCapabilities()`. During `loadProfile()`, fetch profile and capabilities together, store permission codes as a `Set`-backed array, and expose:

```ts
can(permissionCode: string): boolean
canAny(...permissionCodes: string[]): boolean
```

Clear capabilities on logout or failed profile loading. Do not persist capability codes to local storage.

**Step 4: Implement pure suite selection functions**

Keep selection math outside the Vue component. Use explicit bundle `tier` and `categoryId`; never parse suite names or permission-code prefixes.

**Step 5: Run frontend tests and type checks**

```powershell
npm run test -- --run
npm run check
```

Expected: PASS.

**Step 6: Commit**

```powershell
git add frontend/package.json frontend/package-lock.json frontend/src/types frontend/src/api/modules/auth.ts frontend/src/stores/auth.ts frontend/src/views/role/components/permissionSuiteSelection*
git commit -m "feat: add frontend capability state"
```

### Task 6: Replace Flat Permission Buttons with the Module Tier Matrix

**Files:**
- Modify: `frontend/src/views/role/components/RolePermissionDrawer.vue`
- Modify: `frontend/src/views/role/RoleListView.vue`

**Step 1: Render the approved matrix**

Group bundles by `categoryId`, ordered by `categorySort`. Render one compact row per module with module name, description, selected permission summary and an Element Plus segmented control:

```text
未配置 | 常规操作 | 完整管理
```

Show a non-selectable `自定义` status when granular permissions do not exactly match a tier. Keep the existing granular tree below the matrix.

**Step 2: Connect all changes to the pure helper**

Do not duplicate set arithmetic in the component. Synchronize the Element Plus tree after every tier change and preserve full-access roles as read-only.

**Step 3: Add built-in role UI protection**

- disable permission-level editing for built-in roles;
- replace scope selector with a locked scope label for built-in roles;
- exclude built-in roles from selection;
- hide status and delete actions for built-in roles;
- preserve permission authorization and name/remark editing.

**Step 4: Run frontend tests and type checks**

```powershell
Set-Location frontend
npm run test -- --run
npm run check
```

Expected: PASS.

**Step 5: Commit**

```powershell
git add frontend/src/views/role
git commit -m "feat: add module permission suite matrix"
```

### Task 7: Make Management Pages Capability-Aware

**Files:**
- Modify: `frontend/src/views/user/UserListView.vue`
- Modify: `frontend/src/views/department/DepartmentTreeView.vue`
- Modify: `frontend/src/views/role/RoleListView.vue`
- Modify: `frontend/src/views/menu/MenuTreeView.vue`
- Modify: `frontend/src/views/ldap/LdapControlView.vue`
- Modify: `frontend/src/views/system/SystemImportView.vue`
- Modify: `frontend/src/views/system/SystemMailConfigView.vue`
- Modify: `frontend/src/views/sync/SyncJobListView.vue`
- Modify: `frontend/src/views/role-group/RoleGroupManagementView.vue`

**Step 1: Gate user-management operations and lazy-load dependencies**

Use exact permissions for create, edit, access toggle, role assignment, reset, LDAP sync and delete actions. Enable role and department queries only when opening forms that require them. A `USER_READ` + `USER_DETAIL` account must load the page without requesting `/roles` or `/departments/tree`.

**Step 2: Gate department, role and menu mutations**

Hide each mutation control unless `authStore.can(...)` is true. Keep read-only navigation and details intact.

**Step 3: Gate LDAP, import, sync and mail actions**

Use the exact execution/save/test/retry permission for each button. Do not replace backend error handling with frontend assumptions.

**Step 4: Gate role-group mutations**

Allow the page and GET queries with `ROLE_GROUP_READ` or `ROLE_GROUP_MANAGE`. Show group creation/settings/collaborator/role/token mutations only with `ROLE_GROUP_MANAGE`, member assignment only with both required permissions, and still honor OWNER/MANAGER rules returned by the domain model.

**Step 5: Run frontend tests and type checks**

```powershell
Set-Location frontend
npm run test -- --run
npm run check
```

Expected: PASS without adding mobile-only layout rules.

**Step 6: Commit**

```powershell
git add frontend/src/views
git commit -m "feat: align management actions with capabilities"
```

### Task 8: Repair Role-Group Rail Information Hierarchy

**Files:**
- Modify: `frontend/src/views/role-group/RoleGroupManagementView.vue`

**Step 1: Replace long rail identity text**

Return only `所有者`, `协管员`, `平台监管`, or `未加入` in the rail. Keep the detailed identity sentence in the detail header.

**Step 2: Change the card to three stable rows**

- first row: group name and short identity tag;
- second row: role/member statistics across the full width;
- third row: creator across the full width.

Use separate wrappers and stable grid tracks so the identity tag cannot consume the creator row.

**Step 3: Run type checks and production build**

```powershell
Set-Location frontend
npm run check
npm run build
```

Expected: PASS. Do not perform browser, screenshot, Playwright or browser automation checks.

**Step 4: Commit**

```powershell
git add frontend/src/views/role-group/RoleGroupManagementView.vue
git commit -m "fix: stabilize role group rail metadata"
```

### Task 9: Run Full Regression Verification

**Files:**
- Modify as required by failing tests only.

**Step 1: Run all backend tests**

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

Expected: all tests PASS.

**Step 2: Run all frontend verification**

```powershell
Set-Location frontend
npm run test -- --run
npm run check
npm run build
```

Expected: all commands PASS.

**Step 3: Validate migration and API compatibility through HTTP**

Start or restart the local backend through the existing Docker development configuration. Log in with the existing admin account through HTTP and verify:

- `/api/v1/auth/me` retains its fields;
- `/api/v1/auth/capabilities` returns permission codes;
- `/api/v1/permissions/bundles` returns twenty bundles with original and added fields;
- every category has STANDARD and ADMIN;
- `ROLE_GROUP_READ` exists;
- database Flyway history reaches V51.

No browser may be used.

**Step 4: Run Compose validation**

Run the repository's existing Compose config validation command for the offline deployment reference.

Expected: exit code 0 and no unresolved service configuration.

**Step 5: Commit any verification fixes**

```powershell
git add <only-files-required-by-fixes>
git commit -m "test: complete permission suite regression coverage"
```

### Task 10: Package, Track and Rebuild the Incremental Offline Update

**Files:**
- Modify: `build-versions.txt` through the required build script only.
- Replace package contents under: `E:\ldap\LDAP_incremental_update`

**Step 1: Ensure implementation commits are complete**

Run:

```powershell
git status --short
git rev-parse HEAD
```

Expected: no uncommitted implementation changes before versioned packaging.

**Step 2: Build the backend with mandatory tracking**

```powershell
pwsh -File .\scripts\build-with-version-tracking.ps1
```

Expected: package succeeds and `build-versions.txt` receives a line containing the current full commit, JAR name and `V51`.

**Step 3: Commit the generated version record**

```powershell
git add build-versions.txt
git commit -m "chore: record module permission suite build"
```

**Step 4: Rebuild frontend and Docker images**

Use the existing offline image build process and unchanged service names, ports and `.env` contract. Tag the new backend and frontend images consistently with the incremental package manifest.

**Step 5: Replace the incremental package safely**

Move the previous `E:\ldap\LDAP_incremental_update` to a versioned backup directory after validating exact paths. Create the new package with backend/frontend image tar files, checksums, release notes, manifest and command-by-command deployment documentation. Do not include deployment scripts.

**Step 6: Validate the package**

- verify every checksum;
- run `docker load` for every image tar;
- run `docker compose config` against the package/reference Compose configuration;
- start the packaged images on non-conflicting local validation ports;
- check backend health and key APIs through HTTP;
- confirm JAR contains migrations through V51;
- stop only the temporary validation containers.

**Step 7: Final status check**

```powershell
git status --short
git log -8 --oneline
```

Expected: clean worktree; package manifest references the final implementation commit and V51.
