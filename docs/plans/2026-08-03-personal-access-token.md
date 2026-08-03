# Personal Access Token Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add fine-grained personal access tokens whose effective permissions are the immutable selected subset intersected with the account's current permissions.

**Architecture:** Persist opaque token hashes and selected permission IDs in MySQL, authenticate PAT and JWT Bearer credentials through one filter, and authorize by stable permission codes. Keep all existing API and LDAP contracts unchanged while adding a self-service Vue page and additive endpoints.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Security, jCasbin, MyBatis-Plus, Flyway/MySQL, Vue 3, TypeScript, Element Plus, Vitest-free TypeScript build checks.

---

### Task 1: Establish the permission-code authorization contract

**Files:**
- Create: `src/main/java/com/company/idm/infrastructure/security/CredentialType.java`
- Create: `src/main/java/com/company/idm/application/rbac/EffectivePermissionService.java`
- Create: `src/test/java/com/company/idm/application/rbac/EffectivePermissionServiceTest.java`
- Modify: `src/main/java/com/company/idm/infrastructure/security/AuthenticatedUser.java`
- Modify: `src/main/java/com/company/idm/infrastructure/casbin/CasbinAccessService.java`
- Modify: `src/main/java/com/company/idm/infrastructure/casbin/CasbinPolicyService.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/mapper/PermissionMapper.java`
- Modify: `src/main/java/com/company/idm/domain/rbac/RolePolicy.java`

**Steps:**
1. Write tests proving a session receives current account permissions and a PAT receives only the intersection with its selected permission codes.
2. Run `mvn -Dtest=EffectivePermissionServiceTest test` and verify the missing types fail compilation.
3. Extend the authenticated principal with credential type, credential ID and selected permission codes; update every constructor directly.
4. Change Casbin policies to use stable permission codes and add `hasAny(authentication, permissionCodes...)` authorization.
5. Delete the path/action authorization method after all callers are migrated; do not retain an alias.
6. Run the focused test and commit `refactor: authorize requests by permission code`.

### Task 2: Migrate every protected controller to permission codes

**Files:**
- Modify: `src/main/java/com/company/idm/interfaces/user/UserController.java`
- Modify: `src/main/java/com/company/idm/interfaces/department/DepartmentController.java`
- Modify: `src/main/java/com/company/idm/interfaces/role/RoleController.java`
- Modify: `src/main/java/com/company/idm/interfaces/permission/PermissionController.java`
- Modify: `src/main/java/com/company/idm/interfaces/menu/MenuController.java`
- Modify: `src/main/java/com/company/idm/interfaces/ldap/ThirdPartyLdapController.java`
- Modify: `src/main/java/com/company/idm/interfaces/importplan/ImportPlanController.java`
- Modify: `src/main/java/com/company/idm/interfaces/system/MailConfigController.java`
- Modify: `src/main/java/com/company/idm/interfaces/sync/SyncController.java`
- Modify: `src/main/java/com/company/idm/application/user/UserReadScopeService.java`
- Modify: `src/main/java/com/company/idm/application/user/PasswordResetAuthorizationService.java`
- Test: `src/test/java/com/company/idm/application/user/UserReadScopeServiceTest.java`
- Test: `src/test/java/com/company/idm/application/user/PasswordResetAuthorizationServiceTest.java`

**Steps:**
1. Add failing authorization matrix tests for full read, self/subordinate read, and direct/tree/all reset scopes.
2. Replace every path/method expression with stable permission codes, using `hasAny` for shared endpoints.
3. Replace direct full-account permission reads in user list/detail and reset checks with `EffectivePermissionService`.
4. Search with `rg "casbinAccessService.check\(|findPermissionCodesByUserId" src/main/java` and confirm obsolete controller paths are gone.
5. Run user and RBAC tests and commit `refactor: enforce effective permission scopes`.

### Task 3: Add the V46 token schema

**Files:**
- Create: `src/main/resources/db/migration/V46__personal_access_tokens.sql`
- Create: `src/main/java/com/company/idm/domain/token/PersonalAccessToken.java`
- Create: `src/main/java/com/company/idm/domain/token/PersonalAccessTokenRepository.java`
- Create: `src/main/java/com/company/idm/infrastructure/persistence/dataobject/PersonalAccessTokenDO.java`
- Create: `src/main/java/com/company/idm/infrastructure/persistence/dataobject/PersonalAccessTokenPermissionDO.java`
- Create: `src/main/java/com/company/idm/infrastructure/persistence/mapper/PersonalAccessTokenMapper.java`
- Create: `src/main/java/com/company/idm/infrastructure/persistence/mapper/PersonalAccessTokenPermissionMapper.java`
- Create: `src/main/java/com/company/idm/infrastructure/persistence/repository/MybatisPersonalAccessTokenRepository.java`
- Test: `src/test/java/com/company/idm/infrastructure/persistence/repository/MybatisPersonalAccessTokenRepositoryTest.java`

**Steps:**
1. Write repository tests for ownership, permission ordering, revoked tokens and expiry queries.
2. Add `sys_personal_access_token`, `sys_personal_access_token_permission`, required indexes, and nullable credential audit columns.
3. Implement the domain and repository without exposing secret hashes outside infrastructure.
4. Run repository tests and the H2/Flyway test context.
5. Commit `feat: persist personal access tokens`.

### Task 4: Implement secure token generation and validation

**Files:**
- Create: `src/main/java/com/company/idm/application/token/PersonalAccessTokenSecretService.java`
- Create: `src/main/java/com/company/idm/infrastructure/security/Sha256PersonalAccessTokenSecretService.java`
- Create: `src/test/java/com/company/idm/infrastructure/security/Sha256PersonalAccessTokenSecretServiceTest.java`

**Steps:**
1. Write failing tests for prefix parsing, 256-bit randomness, stable hashing, malformed input and constant-time verification behavior.
2. Generate `idm_pat_<tokenUid>_<secret>` with `SecureRandom` and Base64 URL encoding without padding.
3. Store only SHA-256 output and a non-sensitive display prefix; version the hash format as `1`.
4. Run the focused tests and commit `feat: generate opaque personal access tokens`.

### Task 5: Unify JWT and PAT Bearer authentication

**Files:**
- Create: `src/main/java/com/company/idm/application/auth/BearerCredentialAuthenticator.java`
- Create: `src/main/java/com/company/idm/infrastructure/security/JwtBearerCredentialAuthenticator.java`
- Create: `src/main/java/com/company/idm/infrastructure/security/PersonalAccessTokenAuthenticator.java`
- Create: `src/main/java/com/company/idm/infrastructure/security/BearerAuthenticationFilter.java`
- Delete: `src/main/java/com/company/idm/infrastructure/security/JwtAuthenticationFilter.java`
- Modify: `src/main/java/com/company/idm/infrastructure/security/SecurityConfig.java`
- Test: `src/test/java/com/company/idm/infrastructure/security/BearerAuthenticationFilterTest.java`
- Test: `src/test/java/com/company/idm/infrastructure/security/PersonalAccessTokenAuthenticatorTest.java`

**Steps:**
1. Write tests for unchanged JWT authentication, valid PAT, malformed PAT, revoked/expired PAT, disabled/resigned owner and missing credentials.
2. Implement explicit authenticator composition selected by the `idm_pat_` prefix.
3. Reject PAT access to interactive-only password, menu-self and token-management endpoints.
4. Replace the old filter in `SecurityConfig` and delete the old class.
5. Run security tests and commit `feat: authenticate personal access tokens`.

### Task 6: Add token lifecycle application services and APIs

**Files:**
- Create: `src/main/java/com/company/idm/application/token/PersonalAccessTokenApplicationService.java`
- Create: `src/main/java/com/company/idm/application/token/CreatePersonalAccessTokenCommand.java`
- Create: `src/main/java/com/company/idm/interfaces/token/PersonalAccessTokenController.java`
- Create: `src/main/java/com/company/idm/interfaces/token/CreatePersonalAccessTokenRequest.java`
- Create: `src/main/java/com/company/idm/interfaces/token/PersonalAccessTokenResponse.java`
- Create: `src/main/java/com/company/idm/interfaces/token/CreatedPersonalAccessTokenResponse.java`
- Create: `src/main/java/com/company/idm/interfaces/token/AvailableTokenPermissionResponse.java`
- Create: `src/test/java/com/company/idm/application/token/PersonalAccessTokenApplicationServiceTest.java`
- Create: `src/test/java/com/company/idm/interfaces/token/PersonalAccessTokenControllerTest.java`
- Modify: `src/main/java/com/company/idm/infrastructure/config/ApplicationPropertiesConfig.java`
- Modify: `src/main/resources/application.yml`

**Steps:**
1. Write failing tests for permission-subset validation, no automatic expansion, expiry validation, active-token limit, one-time secret response, ownership and idempotent revoke.
2. Add paginated list, available-permission, create and revoke endpoints under `/api/v1/personal-access-tokens`.
3. Require the existing five-minute password verification credential for create and require a session credential for all lifecycle endpoints.
4. Add externally configured active-token limit and last-used write interval.
5. Verify all responses use the existing `ApiResponse` envelope and commit `feat: manage personal access tokens`.

### Task 7: Add usage metadata and credential-aware audit

**Files:**
- Create: `src/main/java/com/company/idm/application/token/PersonalAccessTokenUsageService.java`
- Modify: `src/main/java/com/company/idm/domain/audit/AuditLog.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/dataobject/AuditLogDO.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/repository/MybatisAuditLogRepository.java`
- Modify: `src/main/java/com/company/idm/infrastructure/sql/SqlLogProperties.java`
- Test: `src/test/java/com/company/idm/application/token/PersonalAccessTokenUsageServiceTest.java`

**Steps:**
1. Write tests for throttled last-used updates and audit credential enrichment.
2. Record last-used time/IP after successful authentication without writing on every request.
3. Audit create, revoke and failed PAT authentication without token material.
4. Confirm authorization, token, secret and credential values remain masked in logs.
5. Commit `feat: audit personal access token usage`.

### Task 8: Build the self-service Vue page

**Files:**
- Create: `frontend/src/types/personal-access-token.ts`
- Create: `frontend/src/api/modules/personal-access-token.ts`
- Create: `frontend/src/views/profile/PersonalAccessTokenView.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/stores/menu.ts`
- Modify: `frontend/src/layout/AdminLayout.vue`

**Steps:**
1. Define typed list, available permission, create and revoke contracts matching the backend records.
2. Add `/access-tokens` to the always-allowed authenticated routes and place its key-icon entry below personal center.
3. Implement the token list, status display, pagination, create dialog, permission selection, expiry presets and revoke confirmation.
4. Show the created secret once with copy action; never write it to Pinia persistence or local storage.
5. Run `npm run check` and `npm run build` in `frontend` and commit `feat: add personal access token page`.

### Task 9: Run compatibility and security regression

**Files:**
- Modify: `src/test/java/com/company/idm/test/support/ControllerTestSecurityConfig.java`
- Create: `src/test/java/com/company/idm/infrastructure/security/PersonalAccessTokenAuthorizationIntegrationTest.java`
- Modify: existing controller contract tests as required by direct constructor changes.

**Steps:**
1. Run `mvn test` and fix root causes without compatibility aliases.
2. Verify JWT login and `/api/v1/auth/me` return exactly the existing fields.
3. Verify LDAP classes, configuration and API response DTOs have no behavioral diff.
4. Exercise the complete authorization matrix, including account permission removal after token creation.
5. Run frontend type check and production build; do not run browser automation or screenshots.
6. Commit `test: cover personal access token security`.

### Task 10: Document, benchmark and package

**Files:**
- Modify: `docs/project-design.md`
- Create: `docs/runbooks/personal-access-token-usage.md`
- Modify: offline deployment documentation only when producing the next package.

**Steps:**
1. Document Bearer usage, one-time secret handling, permission intersection, revocation and troubleshooting.
2. Run a fixed JWT/PAT authentication load and record P50/P95, query count and usage-write frequency.
3. Run `mvn clean package` only through the version-tracking process and append the required V46 line to `build-versions.txt`.
4. Build the frontend and backend Docker images and prepare the next additive offline update package when requested.
5. Commit `docs: document personal access token operations`.
