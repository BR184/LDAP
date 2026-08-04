# Personal Access Token Management Redesign Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rebuild personal access token management with recoverable encrypted secrets, grouped risk-aware scopes, follow-account authorization, true deletion, and deterministic UTF-8 repair.

**Architecture:** Keep SHA-256 as the authentication authority and add AES-GCM ciphertext only for owner-initiated reveal. Resolve token permissions through one `FIXED` or `FOLLOW_ACCOUNT` model, backed by a versioned JSON permission-group catalog. Preserve existing LDAP and business API contracts while directly replacing the newly introduced PAT lifecycle behavior.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Security, MyBatis-Plus, Flyway/MySQL 8, Jackson, Vue 3, TypeScript, Element Plus.

---

### Task 1: Persist the approved design

**Files:**
- Create: `docs/plans/2026-08-04-personal-access-token-management-redesign-design.md`
- Create: `docs/plans/2026-08-04-personal-access-token-management-redesign.md`

1. Write the approved authority model, compatibility boundary and security trade-off.
2. Run `git diff --check`.
3. Commit with `docs: design recoverable access token management`.

### Task 2: Add the V47/V48 database authority

**Files:**
- Create: `src/main/resources/db/migration/V47__personal_access_token_management.sql`
- Create: `src/main/resources/db/migration/V48__repair_permission_text_encoding.sql`
- Modify: token domain, DO and repository files under `domain/token` and `infrastructure/persistence`.
- Test: `MybatisPersonalAccessTokenRepositoryTest`.

1. Write failing repository tests for description, `FIXED/FOLLOW_ACCOUNT`, ciphertext/key ID, rotation and physical deletion.
2. Add nullable ciphertext/key ID, description and non-null `scope_mode DEFAULT 'FIXED'`.
3. Implement direct repository mappings and delete/rotate operations.
4. Add deterministic permission-name repair by stable permission code.
5. Run focused repository tests and commit `feat: persist recoverable access token state`.

### Task 3: Implement versioned AES-GCM secret protection

**Files:**
- Create: `PersonalAccessTokenEncryptionProperties.java`.
- Create: `PersonalAccessTokenEncryptionService.java` and AES-GCM implementation.
- Test: encryption service tests.
- Modify: `application.yml`, development start script and deployment env example.

1. Write failing tests for random IVs, successful decrypt, wrong AAD, wrong key, key ID selection and missing configuration.
2. Bind an active key ID and environment-provided key ring.
3. Encrypt with AES-256-GCM and AAD composed from owner ID plus token UID.
4. Keep authentication on SHA-256 only.
5. Run tests and commit `feat: encrypt retrievable access token secrets`.

### Task 4: Establish the permission-group catalog

**Files:**
- Create: `src/main/resources/security/personal-access-token-permission-groups.json`.
- Create: catalog records/service under `application/token`.
- Create: grouped response DTOs.
- Test: catalog parsing, uniqueness and complete active API coverage.

1. Write failing catalog validation tests.
2. Define stable low/high-risk groups covering every current API permission exactly once.
3. Parse the structured JSON resource with Jackson.
4. Intersect each group with the current session account's enabled API permissions.
5. Fail validation for duplicate or ungrouped API codes and commit `feat: group access token permissions`.

### Task 5: Replace the token lifecycle application model

**Files:**
- Modify: `PersonalAccessTokenApplicationService`, commands, secret service and tests.
- Modify: `EffectivePermissionService`, `AuthenticatedUser`, PAT authenticator and tests.

1. Add failing tests for password-free create, fixed permissions, follow-account growth/shrink, reveal ownership, legacy unrecoverable tokens, rotation, revocation and deletion.
2. Remove password-verification dependency and input from token creation.
3. Encrypt every newly generated token while retaining its SHA-256 digest.
4. Carry `scopeMode` in the authenticated principal and resolve `FOLLOW_ACCOUNT` directly from current account permissions.
5. Add audited reveal, rotation, revocation and physical deletion services.
6. Run token/security tests and commit `feat: manage recoverable access tokens`.

### Task 6: Publish the additive grouped/reveal APIs

**Files:**
- Modify: token controller and response/request DTOs.
- Modify: controller tests.

1. Write failing controller contract tests for all new endpoints and no-store headers.
2. Keep existing list and raw available-permission fields unchanged and append new metadata.
3. Add grouped permissions, reveal, rotations and revocations endpoints.
4. Change PAT `DELETE` to physical deletion and update all project callers.
5. Confirm PAT credentials cannot call lifecycle APIs and commit `feat: expose access token lifecycle APIs`.

### Task 7: Harden the UTF-8 deployment chain

**Files:**
- Modify: `application.yml`, `application-dev.yml`, `application-test.yml`.
- Modify: `deploy/.env.internal.example`, development startup script and startup verifier.
- Test: startup verifier and Chinese DTO contract tests.

1. Write failing character-set verification tests.
2. Add explicit Flyway UTF-8 and JDBC utf8mb4 collation settings.
3. Validate production JDBC session encoding at startup.
4. Verify V48 restores canonical Chinese names without heuristic conversion.
5. Commit `fix: enforce utf8 permission metadata`.

### Task 8: Build the GitLab-style desktop creation page

**Files:**
- Create: `frontend/src/views/profile/PersonalAccessTokenCreateView.vue`.
- Create: grouped API/types and clipboard utility.
- Modify: frontend router and token API module.

1. Define typed group, scope mode, create and secret contracts.
2. Add `/access-tokens/new` as an authenticated route without a navigation duplicate.
3. Implement name, description, expiry, follow-account switch, search and collapsed risk-group selection.
4. Remove password input and verification call.
5. Keep raw secret in component memory only and commit `feat: redesign access token creation`.

### Task 9: Rebuild the desktop token list

**Files:**
- Modify: `PersonalAccessTokenView.vue` and styles.

1. Replace the create dialog with navigation to the new page.
2. Add copy/reveal, rotate, revoke and delete operations with confirmation.
3. Show fixed/follow-account state and grouped permission summaries.
4. Show legacy secrets as unrecoverable with a rotation action.
5. Keep desktop-only layout and commit `feat: complete access token inventory actions`.

### Task 10: Compatibility, security and performance regression

**Files:**
- Modify/add backend security and controller tests.
- Modify: `scripts/benchmark_personal_access_token.py` and runbook.

1. Run full `mvn test` and fix root causes without compatibility aliases.
2. Run frontend `npm run check` and `npm run build`.
3. Verify existing login, auth/me, user and LDAP response contracts remain unchanged.
4. Benchmark JWT, fixed PAT and follow-account PAT with 100 requests and concurrency 10.
5. Confirm full tokens never enter list responses or logs.
6. Commit `test: verify access token management security`.

### Task 11: Package, migrate and run

**Files:**
- Modify: `docs/runbooks/personal-access-token-usage.md`, `docs/project-design.md`, `build-versions.txt`.

1. Document encryption key generation, backup separation, HTTPS and old-token rotation.
2. Run `scripts/build-with-version-tracking.ps1` after an implementation commit so the record contains the correct hash and V48.
3. Restart backend, verify Flyway V47/V48 and schema, then rebuild/restart frontend.
4. Verify health, API proxy and zero active benchmark tokens without browser use.
5. Commit `docs: document recoverable access token operations`.
