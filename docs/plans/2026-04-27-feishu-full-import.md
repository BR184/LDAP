# Feishu Full Import Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace split department/user file-import entrypoints with a single Feishu full-import entrypoint that supports `SUPPLEMENT` and `ALIGN` modes.

**Architecture:** Keep internal import stages separate for stability. Add one orchestrator job for full import, extend the document resolver to support unified JSON or XLSX input, and reuse the existing department/user import services plus a dedicated alignment cleanup service.

**Tech Stack:** Spring Boot 3, MyBatis-Plus, Spring Security, Vue 3, Element Plus, JUnit 5, MockMvc, Apache POI

---

### Task 1: Add failing tests for the new full-import backend contract

**Files:**
- Modify: `src/test/java/com/company/idm/test/SyncApplicationServiceTest.java`
- Create: `src/test/java/com/company/idm/test/SystemImportControllerTest.java`

**Steps:**
1. Add a service test for `executeFeishuFullFileImport(...)` to assert the batch records the single document path and import mode.
2. Add a controller test for `POST /api/v1/system/imports/feishu/full`.
3. Run the targeted tests and confirm they fail because the endpoint and service method do not exist yet.

### Task 2: Add failing tests for unified document parsing and orchestration

**Files:**
- Modify: `src/test/java/com/company/idm/test/FeishuImportDocumentResolverTest.java`
- Create: `src/test/java/com/company/idm/test/FeishuFullImportServiceTest.java`

**Steps:**
1. Add resolver coverage for unified JSON bundle input.
2. Add resolver coverage for XLSX bundle input.
3. Add full-import orchestration tests:
   - `SUPPLEMENT` must not trigger cleanup
   - `ALIGN` must trigger user cleanup before department cleanup
4. Run the targeted tests and confirm they fail.

### Task 3: Implement backend full-import orchestration

**Files:**
- Create: `src/main/java/com/company/idm/common/enums/ImportMode.java`
- Create: `src/main/java/com/company/idm/application/sync/feishu/FeishuFullImportService.java`
- Create: `src/main/java/com/company/idm/application/sync/feishu/FeishuFullImportResult.java`
- Create: `src/main/java/com/company/idm/application/sync/feishu/FeishuImportAlignmentService.java`
- Create: `src/main/java/com/company/idm/application/sync/feishu/FeishuFullImportDocument.java`
- Modify: `src/main/java/com/company/idm/application/sync/SyncApplicationService.java`
- Modify: `src/main/java/com/company/idm/application/sync/SyncRequestPayload.java`
- Modify: `src/main/java/com/company/idm/common/enums/SyncJobType.java`
- Modify: `src/main/java/com/company/idm/common/enums/SyncTargetType.java`
- Create: `src/main/java/com/company/idm/application/sync/handler/FeishuFullImportHandler.java`
- Modify: `src/main/java/com/company/idm/application/sync/feishu/FeishuImportDocumentResolver.java`
- Modify: `src/main/java/com/company/idm/application/sync/feishu/FeishuDepartmentImportService.java`
- Modify: `src/main/java/com/company/idm/application/sync/feishu/FeishuUserImportService.java`
- Modify: `pom.xml`

**Steps:**
1. Add the new `ImportMode` enum and preserve backward compatibility in `SyncRequestPayload`.
2. Extend the resolver to parse unified JSON bundles and XLSX files.
3. Add the full-import orchestrator and cleanup service.
4. Add the new sync job handler and `SyncApplicationService` entrypoint.
5. Run the backend targeted tests until green.

### Task 4: Replace the old UI entrypoints with one system import page

**Files:**
- Create: `frontend/src/views/system/SystemImportView.vue`
- Create: `frontend/src/api/modules/system-import.ts`
- Create: `frontend/src/types/system-import.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/constants/navigation.ts`
- Modify: `frontend/src/views/user/UserListView.vue`
- Modify: `frontend/src/views/department/DepartmentTreeView.vue`

**Steps:**
1. Add a single “Feishu full import” page under system management.
2. Support `SUPPLEMENT` and `ALIGN` actions from that page.
3. Remove the old file-import buttons from user and department views.
4. Run the frontend check and fix any issues.

### Task 5: Seed permissions, update docs, and verify

**Files:**
- Create: `src/main/resources/db/migration/V13__feishu_full_import_entry.sql`
- Modify: `docs/project-design.md`

**Steps:**
1. Add the menu and permission seed for the new full-import entrypoint.
2. Update the design doc to reflect the single-entry full-import model.
3. Run:
   - `mvn -q -Dtest=SyncApplicationServiceTest,SystemImportControllerTest,FeishuImportDocumentResolverTest,FeishuFullImportServiceTest test`
   - `mvn -q -DskipTests compile`
   - `npm run check`
