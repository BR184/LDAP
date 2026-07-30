# Import Blocker Resolution Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use test-driven-development to implement this plan task-by-task.

**Goal:** 让文件导入的阻断冲突具备可审计的“按工号合并更新”“排除相关对象”与“取消草稿”出口，并阻止未决冲突对象继续生成可执行变更。

**Architecture:** `ImportBatch` 作为批次状态机负责取消和冲突解决状态迁移；应用服务负责根据稳定 ID 找出关联变更；数据库持久化处理人、处理时间和动作。前端只调用后端用例并根据服务端状态展示能力。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus、Flyway、JUnit 5、Vue 3、TypeScript、Element Plus。

---

### Task 1: 领域状态机回归测试

**Files:**
- Create: `src/test/java/com/company/idm/domain/sync/ImportBatchConflictWorkflowTest.java`
- Modify: `src/main/java/com/company/idm/domain/sync/ImportBatch.java`
- Modify: `src/main/java/com/company/idm/domain/sync/ChangeItem.java`

1. 编写测试：待处理 `BLOCKER` 拒绝确认。
2. 编写测试：排除冲突后，关联变更转为 `SKIPPED` 且批次可以确认。
3. 编写测试：只有 `DRAFT` 批次可以取消，并记录处理人和时间。
4. 运行指定测试，确认因方法不存在而失败。
5. 实现最小领域行为并重新运行测试。

### Task 2: 冲突关联与生成阶段保护

**Files:**
- Create: `src/main/java/com/company/idm/application/sync/importplan/ImportConflictImpactResolver.java`
- Create: `src/test/java/com/company/idm/application/sync/importplan/ImportConflictImpactResolverTest.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportConflictDetector.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportPlanApplicationService.java`

1. 用 `zhangsan/1941` 场景编写失败测试。
2. 基于 `user_id`、`employee_no`、`dept_code`、`external_id` 匹配关联变更。
3. 文件内重复标识生成冲突索引，阻止相关对象生成可执行项。
4. 对象级检测产生冲突后立即停止生成该对象的变更。
5. 运行相关测试确认通过。

### Task 3: 持久化、接口与权限

**Files:**
- Create: `src/main/resources/db/migration/V33__import_conflict_resolution.sql`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/dataobject/ChangeItemDO.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/dataobject/ImportBatchDO.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/repository/MybatisImportBatchRepository.java`
- Modify: `src/main/java/com/company/idm/interfaces/importplan/ImportPlanController.java`
- Modify: `src/main/java/com/company/idm/interfaces/importplan/ImportPlanResponseAssembler.java`

1. 新增冲突处理与批次取消审计字段。
2. 新增 `POST /plan/{batchId}/conflicts/{itemId}/skip`。
3. 新增 `POST /plan/{batchId}/cancel`。
4. 为超级管理员新增两个独立权限。
5. 保持冲突记录，不提供物理删除接口。

### Task 4: 前端操作闭环

**Files:**
- Modify: `frontend/src/api/modules/system-import.ts`
- Modify: `frontend/src/types/system-import.ts`
- Modify: `frontend/src/views/system/SystemImportView.vue`

1. 新增排除冲突和取消草稿 API。
2. 仅将 `PENDING + BLOCKER` 视为未处理阻断。
3. 在冲突行提供“排除”操作，并明确其会跳过关联对象。
4. 在草稿计划提供“取消计划”操作。
5. 刷新批次详情、复核视图和计划列表。

### Task 5: 验证

1. 运行冲突工作流指定测试。
2. 运行完整 Maven 测试。
3. 运行前端类型检查和生产构建。
4. 使用 HTTP 调用验证取消与冲突排除接口，不使用浏览器。
5. 检查 Git diff，确认没有物理删除冲突记录或旧接口双轨逻辑。

### Task 6: 按工号合并更新

**Files:**
- Modify: `src/main/java/com/company/idm/domain/sync/ConflictResolutionAction.java`
- Modify: `src/main/java/com/company/idm/domain/sync/ChangeItem.java`
- Modify: `src/main/java/com/company/idm/domain/sync/ImportBatch.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportConflictDetector.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportConflictImpactResolver.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportPlanApplicationService.java`
- Test: `src/test/java/com/company/idm/domain/sync/ImportBatchConflictWorkflowTest.java`
- Test: `src/test/java/com/company/idm/application/sync/importplan/ImportConflictImpactResolverTest.java`

1. 先编写失败测试：同工号、不同 `user_id` 的冲突可转换为现有账号更新，关联新增或离职项被跳过。
2. 为冲突增加稳定 `conflictCode`，只允许 `EMPLOYEE_NO_OWNED_BY_ANOTHER_USER` 使用 `MERGE_BY_EMPLOYEE_NO`。
3. 合并时保留现有账号 ID、`user_id`、LDAP DN、内网邮箱、密码与角色，按现有差异策略生成更新项。
4. 已处理冲突转为 `SKIPPED` 并记录处理动作、处理人和时间，新增更新项进入正常确认与执行流程。

### Task 7: 接口、迁移与操作界面

**Files:**
- Create: `src/main/resources/db/migration/V34__import_conflict_merge_resolution.sql`
- Modify: `src/main/java/com/company/idm/interfaces/importplan/ImportPlanController.java`
- Modify: `src/main/java/com/company/idm/interfaces/importplan/ImportPlanResponseAssembler.java`
- Modify: `frontend/src/api/modules/system-import.ts`
- Modify: `frontend/src/types/system-import.ts`
- Modify: `frontend/src/views/system/SystemImportView.vue`

1. 新增 `POST /plan/{batchId}/conflicts/{itemId}/merge-by-employee-no`，复用冲突处理权限。
2. V34 新增 `conflict_code` 并为已有“工号属于其他平台用户”冲突回填稳定编码。
3. 接口返回每条冲突的 `resolutionOptions`，前端仅展示服务端允许的操作。
4. 冲突行提供“确认更新”和“排除”操作；确认更新后展示生成的字段变更，计划确认与执行继续使用原状态机。
5. 使用隔离数据库通过 HTTP 验证旧批次也能合并，禁止浏览器验证。

### Task 8: 历史批次候选恢复与真实链路修正

**Files:**
- Create: `src/main/java/com/company/idm/application/sync/importplan/ImportConflictCandidateRecoveryService.java`
- Create: `src/main/java/com/company/idm/application/sync/importplan/ImportUserTargetFactory.java`
- Modify: `src/main/java/com/company/idm/application/sync/feishu/FeishuImportDocumentResolver.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportEmployeeNumberMergeService.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportPlanApplicationService.java`
- Test: `src/test/java/com/company/idm/application/sync/feishu/FeishuImportDocumentResolverHashTest.java`
- Test: `src/test/java/com/company/idm/application/sync/importplan/ImportEmployeeNumberMergeServiceTest.java`

1. 旧冲突缺少 `after_json` 时，依据批次完整 SHA-256 在受控目录恢复原始导入文档，并将恢复扫描上限外置为配置。
2. `user_id`、`employee_no` 必须在源文件内唯一；文件内重复、已有账号交叉命中或源文件缺失时拒绝合并。
3. 抽取唯一的用户目标构建器，计划生成与旧批次恢复共用部门、上级、字段标准化和账号身份保留规则。
4. 恢复的前后快照写回冲突记录；从仓储加载的不可变变更列表也必须能够一次性追加合并更新项。
5. 数值型 XLSX 手机号使用无科学计数法的数值文本解析，验证 `8619999999999` 标准化为 `19999999999`。
6. 在克隆库验证 V34 迁移、冲突处理、计划确认和完整执行，正式数据库与共享 LDAP 不承载验证写入。
