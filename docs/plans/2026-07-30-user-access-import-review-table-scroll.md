# 用户准入、导入审查与表格滚动 Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 建立不受文件导入影响的管理员准入策略，以部门名称展示导入差异，规范化兼职部门存储，并为全部表格提供统一的固定横向滚动条。

**Architecture:** 用 `accessAllowed + employmentStatus` 取代含义混杂的用户状态；导入快照只保存导入拥有字段，由执行器与数据库现状合并。兼职部门迁移到关系表，导入审查通过批次级展示投影解析部门路径。前端通过公共滚动容器和隔离的 Element Plus 适配器统一管理横向滚动。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus、Flyway、JUnit 5、Vue 3、TypeScript、Element Plus、Vitest、Vite。

---

### Task 1: 固化用户准入领域规则

**Files:**
- Create: `src/main/java/com/company/idm/domain/user/UserAccessPolicy.java`
- Create: `src/test/java/com/company/idm/domain/user/UserAccessPolicyTest.java`
- Modify: `src/main/java/com/company/idm/domain/user/User.java`
- Delete: `src/main/java/com/company/idm/common/enums/UserStatus.java`

1. 先编写失败测试：只有 `accessAllowed=true` 且 `employmentStatus=ACTIVE` 时允许登录。
2. 增加离职、管理员关闭、重新在职但仍被管理员关闭等测试。
3. 在 `User` 中用 `boolean accessAllowed` 替换 `UserStatus status`。
4. 实现单一 `UserAccessPolicy`，认证、JWT 和 LDAP 同步后续只调用该策略。
5. 运行 `mvn -Dtest=UserAccessPolicyTest test`，预期全部通过。

### Task 2: 一次性迁移用户准入和兼职部门

**Files:**
- Create: `src/main/resources/db/migration/V35__user_access_and_part_time_department.sql`
- Create: `src/main/java/com/company/idm/infrastructure/persistence/dataobject/UserPartTimeDepartmentDO.java`
- Create: `src/main/java/com/company/idm/infrastructure/persistence/mapper/UserPartTimeDepartmentMapper.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/dataobject/UserDO.java`

1. 为 `sys_user` 新增非空 `access_allowed`，由现有 `status` 回填。
2. 新建 `sys_user_part_time_department`，迁移逗号字段并去重、保序。
3. 删除 `sys_user.status` 与 `sys_user.part_time_dept_codes`。
4. 将权限 `USER_STATUS` 直接迁移为 `USER_ACCESS_UPDATE`，资源改为 `/api/v1/users/:id/access`。
5. 在隔离数据库执行 Flyway，检查迁移前后用户数、关系数、重复数和字段结构。

### Task 3: 重构用户仓储为新权威模型

**Files:**
- Modify: `src/main/java/com/company/idm/domain/user/UserRepository.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/repository/MybatisUserRepository.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/dataobject/UserDO.java`
- Test: `src/test/java/com/company/idm/infrastructure/persistence/repository/MybatisUserRepositoryTest.java`

1. 编写关系集合保存、替换、去重和批量读取测试。
2. 删除文本拆分和拼接方法。
3. 列表查询一次性加载兼职部门关系，并按 `sort_no` 组装。
4. `save` 与 `updateProfile` 在同一事务内同步关系表。
5. 新增原子 `updateAccessAllowed(id, allowed, tokenVersion, operator)`。
6. 将 `findActiveUsers` 改为语义明确的查询，并同步所有调用者。

### Task 4: 建立管理员准入用例与接口

**Files:**
- Create: `src/main/java/com/company/idm/application/user/UpdateUserAccessCommand.java`
- Create: `src/main/java/com/company/idm/interfaces/user/UpdateUserAccessRequest.java`
- Modify: `src/main/java/com/company/idm/application/user/UserApplicationService.java`
- Modify: `src/main/java/com/company/idm/interfaces/user/UserController.java`
- Modify: `src/main/java/com/company/idm/interfaces/user/UserResponse.java`
- Delete: `src/main/java/com/company/idm/application/user/UpdateUserStatusCommand.java`
- Delete: `src/main/java/com/company/idm/interfaces/user/UpdateUserStatusRequest.java`
- Test: `src/test/java/com/company/idm/application/user/UserApplicationServiceAccessTest.java`

1. 编写只有管理员权限可达、关闭递增令牌、离职员工开启仍保持 LDAP 禁用的测试。
2. 新增 `PUT /api/v1/users/{id}/access`，删除旧 `/status` 接口。
3. 审计记录 `USER_ACCESS_CHANGE` 的前后布尔值。
4. 创建账号默认允许使用；手工创建请求显式携带该值。
5. 禁止删除 `SourceType.FEISHU` 用户，并返回引导使用准入开关的业务错误。

### Task 5: 统一认证、JWT 与 LDAP 有效状态

**Files:**
- Modify: `src/main/java/com/company/idm/application/auth/AuthApplicationService.java`
- Modify: `src/main/java/com/company/idm/infrastructure/security/JwtAuthenticationFilter.java`
- Modify: `src/main/java/com/company/idm/application/sync/handler/LdapReconcileUserHandler.java`
- Modify: `src/main/java/com/company/idm/application/user/UserApplicationService.java`
- Test: `src/test/java/com/company/idm/application/auth/AuthApplicationServiceAccessTest.java`

1. 编写不允许使用、离职、重新在职但不允许使用的认证测试。
2. 认证服务和 JWT 过滤器统一调用 `UserAccessPolicy`。
3. LDAP 创建、更新、手工准入和对账统一按有效状态启停。
4. 搜索并删除所有 `UserStatus` 引用，确认不存在第二套有效状态判断。

### Task 6: 收窄导入快照并保护准入策略

**Files:**
- Modify: `src/main/java/com/company/idm/application/sync/importplan/UserImportSnapshot.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportUserTargetFactory.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportExecutionApplicationService.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportRollbackApplicationService.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportPlanApplicationService.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportEmployeeNumberMergeService.java`
- Test: `src/test/java/com/company/idm/application/sync/importplan/ImportExecutionApplicationServiceTest.java`
- Test: `src/test/java/com/company/idm/application/sync/importplan/ImportEmployeeNumberMergeServiceTest.java`

1. 编写导入更新、工号合并、离职、撤回均保留 `accessAllowed=false` 的失败测试。
2. 从快照删除准入、令牌和 LDAP 本地字段。
3. 更新执行改为“导入字段覆盖现有实体”，创建时默认 `accessAllowed=true`。
4. 离职只修改 `employmentStatus/accountStatus`，有效 LDAP 状态由策略计算。
5. 撤回只恢复导入拥有字段。

### Task 7: 引入稳定导入字段键

**Files:**
- Create: `src/main/java/com/company/idm/domain/sync/ImportFieldKey.java`
- Create: `src/main/resources/db/migration/V36__normalize_import_field_keys.sql`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportDiffPolicyService.java`
- Modify: `src/main/java/com/company/idm/domain/sync/ChangeItem.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/dataobject/ChangeItemDO.java`
- Modify: `src/main/java/com/company/idm/infrastructure/persistence/repository/MybatisImportBatchRepository.java`
- Test: `src/test/java/com/company/idm/application/sync/importplan/ImportDiffPolicyServiceTest.java`

1. 为所有可导入字段定义稳定枚举键和中文标签。
2. 迁移已有 camelCase 字段名为新键。
3. 差异策略只产生枚举键，不再写 Java 属性名。
4. 更新变更摘要和持久化映射，删除旧字段名回退。

### Task 8: 构建部门展示投影

**Files:**
- Create: `src/main/java/com/company/idm/application/sync/importplan/ImportReviewDisplayService.java`
- Modify: `src/main/java/com/company/idm/interfaces/importplan/FieldChangeResponse.java`
- Modify: `src/main/java/com/company/idm/interfaces/importplan/UserReviewRowResponse.java`
- Modify: `src/main/java/com/company/idm/interfaces/importplan/ImportPlanResponseAssembler.java`
- Test: `src/test/java/com/company/idm/application/sync/importplan/ImportReviewDisplayServiceTest.java`

1. 测试现存部门、新建部门、部门改名、兼职多部门和缺失部门场景。
2. 构建批次级部门目录，同时读取数据库与批次前后快照。
3. `FieldChangeResponse` 返回 `fieldKey`、`fieldLabel` 和格式化值。
4. 用户审查行返回 `departmentName` 与 `departmentPath`，删除 `deptCode` 展示字段。
5. 摘要使用中文字段标签。

### Task 9: 完整修正文件兼职部门解析

**Files:**
- Modify: `src/main/java/com/company/idm/application/sync/feishu/FeishuImportDocumentResolver.java`
- Modify: `src/main/java/com/company/idm/application/sync/importplan/ImportUserTargetFactory.java`
- Test: `src/test/java/com/company/idm/application/sync/feishu/FeishuImportDocumentResolverDepartmentTest.java`

1. 用逗号、中文逗号、分号、中文分号、换行和重复路径编写参数化测试。
2. 使用统一解析规则输出有序、去重的部门路径。
3. 明确第一条为主部门，其余为兼职部门，并排除主部门重复。
4. 验证所有兼职部门都能映射到稳定部门编码。

### Task 10: 更新用户接口和兼职部门展示

**Files:**
- Create: `src/main/java/com/company/idm/interfaces/user/DepartmentReferenceResponse.java`
- Modify: `src/main/java/com/company/idm/application/user/UserApplicationService.java`
- Modify: `src/main/java/com/company/idm/interfaces/user/UserResponse.java`
- Modify: `frontend/src/types/user.ts`
- Modify: `frontend/src/views/user/UserListView.vue`
- Modify: `frontend/src/views/user/components/UserDetailDrawer.vue`
- Modify: `frontend/src/views/user/components/UserFormDrawer.vue`

1. 后端同时返回兼容既有接入工具的 `partTimeDeptCodes`、`partTimeDeptNames` 有序数组，以及带完整路径的 `partTimeDepartments` 对象列表。三个字段共享同一有序兼职部门来源；名称缺失时旧名称数组回退部门编码。
2. 前端列表按完整路径逐行展示，不使用单行省略。
3. 详情抽屉完整展示全部兼职部门。
4. 编辑表单从对象列表提取稳定编码提交。
5. 用户列表新增“允许使用”开关、确认交互和独立 API。

### Task 11: 更新导入审查前端

**Files:**
- Modify: `frontend/src/types/system-import.ts`
- Modify: `frontend/src/views/system/SystemImportView.vue`

1. 类型改为 `fieldKey/fieldLabel` 和部门展示字段。
2. 展开详情使用 `fieldLabel`，部门多值按换行渲染。
3. 主表部门列显示完整路径并参与搜索。
4. 搜索确认模板和 TypeScript 中不再存在 `deptCode`、`partTimeDeptCodes` 展示引用。

### Task 12: 实现公共横向滚动基础设施

**Files:**
- Create: `frontend/src/components/table-scroll/table-scroll-math.ts`
- Create: `frontend/src/components/table-scroll/element-table-scroll-adapter.ts`
- Create: `frontend/src/components/table-scroll/persistent-table-scroll-coordinator.ts`
- Create: `frontend/src/components/table-scroll/PersistentTableScrollFrame.vue`
- Create: `frontend/src/components/table-scroll/table-scroll-math.test.ts`
- Modify: `frontend/src/styles/index.scss`
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Modify: `frontend/vite.config.ts`

1. 用 Vitest 覆盖滑块尺寸、比例换算、左右边界和零溢出。
2. 实现隔离的 Element Plus 适配器和活动表格协调器。
3. 实现固定轨道、Pointer Capture、键盘操作和拖动禁选状态。
4. 将构建目标固定为 Chrome/Edge 80、Firefox 78、Safari 13.1。
5. 运行 `npm run test`、`npm run check` 和 `npm run build`。

### Task 13: 全量接入表格页面

**Files:**
- Modify: `frontend/src/views/user/UserListView.vue`
- Modify: `frontend/src/views/role/RoleListView.vue`
- Modify: `frontend/src/views/sync/SyncJobListView.vue`
- Modify: `frontend/src/views/system/SystemImportView.vue`
- Modify: `frontend/src/views/ldap/LdapControlView.vue`

1. 用 `PersistentTableScrollFrame` 包裹所有横向可能溢出的主表格与详情表格。
2. 删除页面级横向滚动样式和重复逻辑。
3. 确认原生横向条只在公共组件作用域内隐藏。
4. 运行类型检查和生产构建。

### Task 14: 完整验证和旧路径清理

1. 运行 `mvn clean package`，预期所有后端测试通过。
2. 运行 `npm run test && npm run check && npm run build`，预期全部通过。
3. 通过 `rg` 确认应用代码不存在 `UserStatus`、旧 `/status` 接口、`part_time_dept_codes` 和旧导入字段键；对外兼容响应字段 `partTimeDeptNames` 除外。
4. 在隔离数据库验证 V35/V36 迁移和兼职关系数量。
5. 使用 HTTP 调用验证管理员关闭后重新导入仍保持关闭；不使用浏览器。
6. 运行 `git diff --check`，检查没有空白错误或意外生成文件。
7. 更新本设计文档中的实际偏差与最终验证结果。

## 实际实施记录（2026-07-30）

### 已完成

- 用 `accessAllowed + employmentStatus` 取代旧 `UserStatus`，删除用户旧状态接口与调用路径。
- 导入执行、工号合并、撤回、LDAP 对账及认证统一遵守 `UserAccessPolicy`；人员文件不会覆盖管理员的准入决定。
- 兼职部门迁移到 `sys_user_part_time_department` 关系表，用户接口返回带完整层级路径的 `partTimeDepartments`，并保留 `partTimeDeptCodes`、`partTimeDeptNames` 作为已发布 API 的兼容字段。三者按关系表 `sort_no` 保持同一顺序；名称缺失时名称数组回退部门编码。
- 导入差异使用 `ImportFieldKey` 持久化，并通过 `ImportReviewDisplayService` 只展示中文字段标签、部门名称和完整路径。无法解析部门时统一显示“部门信息缺失”。
- 文件花名册的部门路径支持英文逗号、中文逗号、英文分号、中文分号和换行分隔，按源顺序去重后确定主部门与兼职部门。
- 用户列表新增独立的“允许使用”管理员开关；文件导入不会更改该值。
- 所有已使用 Element Plus 表格的页面已接入 `PersistentTableScrollFrame`，横向滚动的 DOM 适配和交互逻辑集中在公共组件中。
- 补充回归测试覆盖导入资料更新时保留管理员准入、共享导入快照的本地字段保护、文件兼职部门解析，以及导入审查中的部门路径和缺失部门展示。

### 本次验证

- `frontend`: `npm run check` 通过。
- `frontend`: `npm run build` 通过；Vite 仅报告现有主包体积警告。
- 后端：`mvn clean package` 通过，45 个测试全部成功。
- `git diff --check` 通过，无空白错误。
- 应用代码检索确认不再引用 `UserStatus`、旧用户状态接口、`part_time_dept_codes` 或旧导入 `fieldName`。`partTimeDeptNames` 仅作为对外 API 兼容字段保留；角色自身的 `/status` 接口不属于本次替换范围。

### 受环境限制的验证

- V35/V36 Flyway 迁移和管理员关闭后再次导入的 HTTP 集成验证，应在隔离 MySQL 数据库与可启动后端中执行，避免影响现有数据。
- 遵守浏览器验证禁令，未进行页面自动化、截图或浏览器交互验证。
- 现有前端工具链未声明 `npm test` 或 Vitest；固定滚动的纯数学逻辑已纳入 TypeScript 类型检查和生产构建，自动化运行器需在具备可写 npm 缓存的构建环境中补齐。
