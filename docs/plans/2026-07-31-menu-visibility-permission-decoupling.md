# Menu Visibility Permission Decoupling Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Decouple navigation visibility from business and API permissions, so every actionable left-navigation page is controlled by its own stable menu-visibility permission.

**Architecture:** `sys_role_permission` becomes the only role-grant authority. API permissions continue to protect API operations and data ranges; `MENU` permissions control only navigation visibility. Each grantable page has exactly one `MENU_VIEW_<MENU_CODE>` permission and one `sys_menu_permission` association. Catalog nodes are derived from visible child pages so no role can receive an empty navigation folder.

**Tech Stack:** Java 17, Spring Boot 3.3, MyBatis-Plus, Flyway, MySQL 8, Casbin, Vue 3, TypeScript, Element Plus, JUnit 5, Mockito.

---

## Approved Target Model

### Authority boundaries

| Concern | Authority | Example |
| --- | --- | --- |
| Business data range | API permission | `USER_READ_SELF_AND_SUBORDINATE_TREE` controls which people `/api/v1/users` returns. |
| API operation | API permission | `USER_PASSWORD_RESET_DIRECT` allows the reset API, subject to the target-user scope check. |
| Navigation visibility | MENU permission | `MENU_VIEW_USER_MANAGEMENT` controls whether the User Management entry is included in `/api/v1/menus/self/tree`. |

`USER_READ`, `USER_READ_SELF_AND_SUBORDINATE_TREE`, and every other API permission must have no `sys_menu_permission` mapping after the migration. Granting an API permission after the migration must never change the left navigation.

### Menu visibility permissions

The technical root `UINIT0` and catalog nodes (`PERSONNEL_MANAGEMENT`, `SYSTEM_MANAGEMENT`, `LOG_MANAGEMENT`) are not independently grantable. They have no page content; `RbacApplicationService.includeAncestors` derives them whenever a descendant page is visible. This prevents empty folders while retaining one explicit permission for every usable navigation page.

| Menu code | New permission code | New permission name | Permission resource |
| --- | --- | --- | --- |
| `USER_MANAGEMENT` | `MENU_VIEW_USER_MANAGEMENT` | `菜单显示_用户管理` | `menu:USER_MANAGEMENT` |
| `GROUP_MANAGEMENT` | `MENU_VIEW_GROUP_MANAGEMENT` | `菜单显示_分组管理` | `menu:GROUP_MANAGEMENT` |
| `FIELD_RELATION_MANAGEMENT` | `MENU_VIEW_FIELD_RELATION_MANAGEMENT` | `菜单显示_字段关系管理` | `menu:FIELD_RELATION_MANAGEMENT` |
| `API_MANAGEMENT` | `MENU_VIEW_API_MANAGEMENT` | `菜单显示_接口管理` | `menu:API_MANAGEMENT` |
| `MENU_MANAGEMENT` | `MENU_VIEW_MENU_MANAGEMENT` | `菜单显示_菜单管理` | `menu:MENU_MANAGEMENT` |
| `ROLE_MANAGEMENT` | `MENU_VIEW_ROLE_MANAGEMENT` | `菜单显示_角色管理` | `menu:ROLE_MANAGEMENT` |
| `FILE_IMPORT_MANAGEMENT` | `MENU_VIEW_FILE_IMPORT_MANAGEMENT` | `菜单显示_文件导入` | `menu:FILE_IMPORT_MANAGEMENT` |
| `MAIL_CONFIG_MANAGEMENT` | `MENU_VIEW_MAIL_CONFIG_MANAGEMENT` | `菜单显示_邮件配置` | `menu:MAIL_CONFIG_MANAGEMENT` |
| `OPERATION_LOG` | `MENU_VIEW_OPERATION_LOG` | `菜单显示_同步任务` | `menu:OPERATION_LOG` |

Every new record uses `permission_type = MENU`, `action = VIEW`, and a `resource_path` based on the stable menu code rather than a mutable route path. MENU permissions must be excluded from Casbin HTTP policies; only `permission_type = API` contributes a Casbin resource/action policy.

### Breaking internal changes

`sys_role_menu`, the `/api/v1/roles/{id}/menus` endpoints, role-menu commands, and the separate front-end menu-binding drawer will be removed. The role permission drawer becomes the single place to grant both API and menu visibility permissions. No old alias, forwarding endpoint, or fallback query remains.

## Migration Requirements

The migration must retain access at cutover without preserving the old architecture:

1. Create the nine MENU permission records and one menu-to-permission association per grantable page.
2. Copy every current `sys_role_menu` page binding into the equivalent `sys_role_permission` MENU permission grant. Catalog-only bindings intentionally produce no grant because catalogs are derived.
3. Grant `MENU_VIEW_USER_MANAGEMENT` once to every role that currently has `USER_READ` or `USER_READ_SELF_AND_SUBORDINATE_TREE`, preserving the current User Management entry for global readers and managers.
4. Remove both current API mappings from `USER_MANAGEMENT` (`USER_READ` and `USER_READ_SELF_AND_SUBORDINATE_TREE`).
5. Remove all remaining `sys_role_menu` rows and drop `sys_role_menu`.
6. Add a unique constraint to `sys_menu_permission.menu_id`, enforcing exactly zero or one visibility permission per menu. Catalog menus intentionally have zero.
7. Remove the obsolete `sys_menu.min_permission_level` data and code path, because authorization now belongs to the generated MENU permission rather than a role-menu binding.

The migration uses IDs and menu codes, never display names or physical routes. Existing full-access roles continue to receive all permissions through the normal full-access grant path.

## Task 1: Lock the new navigation contract in tests

**Files:**
- Modify: `src/test/java/com/company/idm/application/rbac/RbacApplicationServiceMenuVisibilityTest.java`
- Create: `src/test/java/com/company/idm/infrastructure/persistence/mapper/MenuMapperAccessTest.java`

**Step 1: Write failing service tests**

Cover these cases:

```java
// MENU_VIEW_USER_MANAGEMENT shows root + Personnel Management + User Management.
// USER_READ alone returns no navigation page.
// A page permission never reveals an unrelated page.
```

**Step 2: Write failing persistence integration tests**

Verify the repository query returns only menus matched by MENU permissions and ignores API permissions and role-menu bindings.

**Step 3: Run the focused tests**

Run:

```powershell
.\.tools\apache-maven-3.9.6\bin\mvn.cmd test "-Dtest=RbacApplicationServiceMenuVisibilityTest,MenuMapperAccessTest"
```

Expected: the new assertions fail before implementation.

**Step 4: Commit the test baseline**

```powershell
git add src/test/java/com/company/idm/application/rbac
git commit -m "test: define menu visibility permission contract"
```

## Task 2: Make menu visibility permissions the sole navigation query input

**Files:**
- Modify: `src/main/java/com/company/idm/domain/rbac/MenuRepository.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/repository/MybatisMenuRepository.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/mapper/MenuMapper.java`
- Modify: `src/main/java/com/company/idm/application/rbac/RbacApplicationService.java`

**Step 1: Replace the repository contract**

Replace `findByAccess(Set<String> roleCodes, Set<String> permissionCodes)` with `findVisibleByPermissionCodes(Set<String> permissionCodes)`. Update every caller in the same commit.

**Step 2: Replace the SQL access model**

The mapper must join `sys_menu_permission` and `sys_permission`, require `p.permission_type = 'MENU'`, and match only the operator's permission codes. Remove all joins and fallback conditions involving `sys_role_menu` and role codes.

**Step 3: Keep ancestor derivation unchanged**

`includeAncestors` remains the only mechanism for returning `UINIT0` and catalog nodes. It must not make a catalog visible when no child page is granted.

**Step 4: Run focused tests**

Run the Task 1 command. Expected: PASS.

**Step 5: Commit**

```powershell
git add src/main/java/com/company/idm/domain/rbac/MenuRepository.java src/main/java/com/company/idm/infrastructure/persistence/repository/MybatisMenuRepository.java src/main/java/com/company/idm/infrastructure/persistence/mapper/MenuMapper.java src/main/java/com/company/idm/application/rbac/RbacApplicationService.java src/test/java/com/company/idm
git commit -m "refactor: resolve navigation from menu permissions only"
```

## Task 3: Separate Casbin HTTP policy generation from MENU permissions

**Files:**
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/mapper/PermissionMapper.java`
- Create: `src/test/java/com/company/idm/infrastructure/persistence/mapper/PermissionMapperPolicyTest.java`

**Step 1: Write the failing policy test**

Grant a role one API permission and one MENU permission. Assert that `selectRolePolicies()` returns only the API resource/action pair.

**Step 2: Filter the mapper query**

Add `p.permission_type = 'API'` to `selectRolePolicies`. Do not encode MENU permissions as synthetic HTTP policies.

**Step 3: Run focused tests**

Run:

```powershell
.\.tools\apache-maven-3.9.6\bin\mvn.cmd test "-Dtest=PermissionMapperPolicyTest"
```

Expected: PASS.

**Step 4: Commit**

```powershell
git add src/main/java/com/company/idm/infrastructure/persistence/mapper/PermissionMapper.java src/test/java/com/company/idm/infrastructure/persistence/mapper/PermissionMapperPolicyTest.java
git commit -m "fix: exclude menu visibility from Casbin API policies"
```

## Task 4: Add menu-permission lifecycle management

**Files:**
- Create: `src/main/java/com/company/idm/application/rbac/MenuVisibilityPermissionService.java`
- Modify: `src/main/java/com/company/idm/domain/rbac/PermissionRepository.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/repository/MybatisPermissionRepository.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/mapper/PermissionMapper.java`
- Modify: `src/main/java/com/company/idm/domain/rbac/RoleRepository.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/repository/MybatisRoleRepository.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/mapper/RolePermissionMapper.java`
- Modify: `src/main/java/com/company/idm/application/rbac/RbacApplicationService.java`
- Test: `src/test/java/com/company/idm/application/rbac/MenuVisibilityPermissionServiceTest.java`

**Step 1: Write failing lifecycle tests**

```java
// Creating a MENU node creates MENU_VIEW_<menuCode> and its menu association.
// Deleting that menu removes role grants, association, and generated permission.
// CATALOG nodes do not create an independently grantable visibility permission.
```

**Step 2: Implement one focused service**

`MenuVisibilityPermissionService` owns deterministic code generation, creation, lookup, and cleanup. It uses the stable `menuCode`, not a route or display name. Do not duplicate prefix logic across `RbacApplicationService` methods.

**Step 3: Integrate transactionally**

Call the service from menu create and delete flows inside their existing transactions. A failed permission operation must roll back the menu operation.

**Step 4: Run focused tests**

Run:

```powershell
.\.tools\apache-maven-3.9.6\bin\mvn.cmd test "-Dtest=MenuVisibilityPermissionServiceTest"
```

Expected: PASS.

**Step 5: Commit**

```powershell
git add src/main/java/com/company/idm/application/rbac src/main/java/com/company/idm/domain/rbac src/main/java/com/company/idm/infrastructure/persistence src/test/java/com/company/idm/application/rbac
git commit -m "feat: maintain generated menu visibility permissions"
```

## Task 5: Remove the legacy role-menu model from backend code

**Files:**
- Delete: `src/main/java/com/company/idm/application/rbac/BindRoleMenusCommand.java`
- Delete: `src/main/java/com/company/idm/interfaces/role/BindRoleMenusRequest.java`
- Delete: `src/main/java/com/company/idm/infrastructure/persistence/dataobject/RoleMenuDO.java`
- Delete: `src/main/java/com/company/idm/infrastructure/persistence/mapper/RoleMenuMapper.java`
- Modify: `src/main/java/com/company/idm/domain/rbac/RoleRepository.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/repository/MybatisRoleRepository.java`
- Modify: `src/main/java/com/company/idm/domain/rbac/MenuRepository.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/repository/MybatisMenuRepository.java`
- Modify: `src/main/java/com/company/idm/application/rbac/RbacApplicationService.java`
- Modify: `src/main/java/com/company/idm/application/rbac/DefaultPermissionLevelRuleService.java`
- Modify: `src/main/java/com/company/idm/interfaces/role/RoleController.java`

**Step 1: Remove role-menu APIs and calls**

Delete `GET /api/v1/roles/{id}/menus` and `PUT /api/v1/roles/{id}/menus`, their commands, validators, audit operation, repositories, and all call sites. Do not retain aliases or no-op endpoints.

**Step 2: Remove role-menu permission-level behavior**

Delete menu-binding authorization checks and the now-unused menu `min_permission_level` logic from the domain, DTOs, forms, and database migration path. Role permission level continues to control who can administer roles and permissions, not which navigation records are assigned.

**Step 3: Simplify default full access**

For full-access roles, `applyDefaultAccessGrants` assigns all permissions, including MENU permissions, and no longer calls `bindMenus`.

**Step 4: Search for old symbols**

Run:

```powershell
rg -n "RoleMenu|roleMenu|bindMenus|ROLE_MENU_BIND|/roles/.*/menus|minPermissionLevel" src frontend
```

Expected: no executable source references; historical Flyway migrations are the only permitted matches.

**Step 5: Commit**

```powershell
git add -A src/main/java frontend/src
git commit -m "refactor: remove role-menu binding model"
```

## Task 6: Replace the role-management menu UI with one permission surface

**Files:**
- Delete: `frontend/src/views/role/components/RoleMenuDrawer.vue`
- Modify: `frontend/src/views/role/RoleListView.vue`
- Modify: `frontend/src/api/modules/role.ts`
- Modify: `frontend/src/views/role/components/RolePermissionDrawer.vue`
- Modify: `frontend/src/views/permission/PermissionTreeView.vue`

**Step 1: Remove the menu binding drawer**

Remove menu tree queries, menu drawer state, mutation, actions, and the `fetchRoleMenuIds` / `bindRoleMenus` API functions. Role management exposes only the permission authorization drawer.

**Step 2: Make MENU permissions legible**

Display MENU permissions as `菜单显示` entries using their stable menu resource and Chinese permission name. Update the full-access notice from “全部接口权限” to “全部权限”.

**Step 3: Replace the static permission-tree page**

Use `/api/v1/permissions/tree` instead of the existing hard-coded two-item example so administrators see the same API and MENU permissions used by role authorization.

**Step 4: Validate the frontend build**

Run:

```powershell
Set-Location frontend
npm run build
```

Expected: successful TypeScript and Vite build.

**Step 5: Commit**

```powershell
git add frontend/src
git commit -m "refactor: manage menu visibility through permissions"
```

## Task 7: Migrate database state and remove old schema

**Files:**
- Create: `src/main/resources/db/migration/V42__menu_visibility_permissions.sql`
- Create: `src/main/resources/db/migration/V43__remove_legacy_role_menu_model.sql`

**Step 1: Write V42 as an idempotent data migration**

It must create the nine MENU permissions, associate each page with exactly one visibility permission, copy legacy page grants from `sys_role_menu` to `sys_role_permission`, and add the User Management visibility grant to roles that currently hold either user-read scope. It then deletes the two API-to-User-Management mappings.

**Step 2: Verify migration state before deleting legacy data**

Use SQL assertions in V42 or a migration integration test to verify:

```sql
-- Each MENU page has exactly one MENU visibility permission.
-- USER_MANAGEMENT has no API permission association.
-- ADMIN and SUPER_ADMIN retain all nine MENU permissions.
-- DIRECT_MANAGER and TREE_MANAGER retain MENU_VIEW_USER_MANAGEMENT.
```

**Step 3: Write V43 cleanup**

Remove residual role-menu rows, drop `sys_role_menu`, remove the obsolete menu permission-level column and constraints, and make `sys_menu_permission.menu_id` unique.

**Step 4: Validate Flyway against the existing development database**

Build and start the backend. Query `flyway_schema_history` and the permission grants listed above. Confirm no user data, LDAP schema, or public LDAP API response changes.

**Step 5: Commit**

```powershell
git add src/main/resources/db/migration
git commit -m "feat: migrate navigation to menu visibility permissions"
```

## Task 8: Final regression and deployment verification

**Files:**
- Modify: relevant unit and mapper tests from Tasks 1-4
- Modify: `build-versions.txt` only through the required packaging record

**Step 1: Run backend tests**

```powershell
.\.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

Expected: all tests pass.

**Step 2: Build the deployable JAR**

```powershell
.\.tools\apache-maven-3.9.6\bin\mvn.cmd clean package -DskipTests
```

Immediately append the required build record to `build-versions.txt` using the current full commit SHA, JAR name, and latest Flyway version (`V43`).

**Step 3: Restart the local backend**

```powershell
.\scripts\start-backend-dev.ps1
Invoke-RestMethod http://127.0.0.1:8083/actuator/health
```

Expected: `{"status":"UP"}`.

**Step 4: Verify authorization cases through HTTP, not browser automation**

- A role with only `USER_READ` cannot see User Management.
- A role with `USER_READ` plus `MENU_VIEW_USER_MANAGEMENT` sees the page and all users.
- A direct manager with `USER_READ_SELF_AND_SUBORDINATE_TREE` plus `MENU_VIEW_USER_MANAGEMENT` sees only self and descendants.
- A role with `MENU_VIEW_ROLE_MANAGEMENT` but no `ROLE_READ` sees the entry but receives authorization denial when loading its data; deployment grants must therefore assign both permissions together when the page needs to work.

**Step 5: Final commit**

```powershell
git add -A
git commit -m "feat: decouple navigation visibility from API permissions"
```
