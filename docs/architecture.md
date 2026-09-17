## architecture.md 文档总则

> 本文档为项目的技术架构权威记录。AGENTS.md 中关于文档职责、更新触发条件和事实优先级的规则同样适用于本文件。
> AI 读取本文件时应将其中内容视为当前技术事实；修改项目代码前必须确认相关架构约定，任何与本文矛盾的设计需先修正本文或取得用户确认。
> 本文档与 `product.md`、`progress.md` 职责分离：产品需求与用户承诺写入 product；当前进度、阻塞项与下一步写入 progress；系统边界、模块职责、数据流、不变量、性能约束、关键决策及技术风险写入 architecture。
> 内容组织以模块为单元，每模块陈述：职责、对外接口、内部核心模型、数据所有权、关键不变量、性能目标、已知限制与演进方向。避免重复 product 的内容，只描述技术“是什么”和“必须满足什么”。
> 更新触发：新增/移除/重构模块、变更模块边界或数据所有权、修改不变量或性能契约、技术选型替换、发现并确认长期技术风险。纯实现细节、临时工具或测试辅助代码不进入本文。
> 保持行文紧凑，优先用代码标识、ID、数字和简短断言，避免叙述性解释和展示性列表。所有陈述必须可被验证（代码、测试、运行结果），否则不得写入。

---

# LDAP 企业统一身份管理平台 — 技术架构

## 技术栈

- 后端：Java 17 / Spring Boot 3.3.0 / Spring Security + JWT / MyBatis-Plus 3.5.7 / Casbin (jcasbin 1.74) / Flyway / Spring LDAP / Spring Mail / MySQL 8.0（测试 H2）。
- 前端：Vue 3.5 / TypeScript / Vite 8 / Element Plus 2.13 / Pinia 3 / TanStack Vue Query 5.99 / Axios / Sass。
- 构建：Maven（`.tools/apache-maven-3.9.6` 自包含）、前端 npm。
- 部署：Docker Compose（`deploy/docker-compose-dev.yml`，后端容器 `corp-idm-backend-dev`:8083 挂载 JAR、前端容器 `corp-idm-web`:5173→nginx、MySQL:3307、OpenLDAP:389）。

## 分层架构（DDD）

```
interfaces (REST Controller / DTO) → application (用例编排/事务) → domain (实体/仓储接口/业务规则) → infrastructure (持久化/外部集成/配置)
```

- 分层依赖单向向下；禁止 Controller 直接操作 DAO、跨层共享可变状态。
- 包根：`com.company.idm.{boot,application,domain,infrastructure,interfaces,common}`；`common.api` 放统一响应，`common.exception` 放业务异常，`common.log` 放 traceId 常量。
- 持久化：`infrastructure.persistence.dataobject`(DO) / `.mapper`(MyBatis-Plus) / `.repository`(仓储实现)；DO 不得直接暴露到接口层，接口层用 record/VO。

## 统一响应与异常

- V1：`ApiResponse<T> { success, code, message, data }`。
- V2：`ApiResponseV2<T> { success, code, message, data, traceId }`；traceId 由 `TraceIdFilter`（`infrastructure.logging`）写入 MDC 与 `X-Trace-Id` 响应头，响应读取 `MDC.get("traceId")`。
- 分页：`PageResult<T> { items, total, pageNum, pageSize }`；列表一律服务端分页（pageSize 上限 100）。
- 错误码字符串三段式（`USER_NOT_FOUND`、`PARAM_INVALID`…），集中在 `ErrorCodeConstants`；HTTP 状态表达大类，`code` 表达细节。
- `GlobalExceptionHandler` 按 URI 前缀 `/api/v2` 分支返回 V1/V2 信封。

## API 版本（V1 兼容层 / V2 标准）

- **V2（标准，新功能一律走此）**：`/api/v2/**`，16 个模块控制器位于 `interfaces/v2/`，复用现有 application 服务与 DTO。
- **V1（兼容层，仅存量/第三方）**：`/api/v1/**`，全部控制器标注 `@Deprecated`；契约不变。修改 V1 必须与用户确认（见 AGENTS.md 对外 API 兼容契约）。
- 鉴权：Casbin 权限码级（`enforcer.enforce(userId, permissionCode, "GRANT")`），**与 URL 无关**，V2 复用同批权限码，无迁移。`sys_permission.resource_path` 仅用于菜单/权限树展示，不参与判权。
- V1 `GET /api/v1/users` 的 `userId` 参数为**精确匹配**，模糊搜索迁移到 `keyword` 参数（修复 lumen_flow 目录同步歧义）。

## 用户模块（旗舰，V2 参照实现）

- 端点：`GET /v2/users`（组合筛选+服务端分页）、`GET /v2/users/by-user-id/{userId}`（精确身份查询，走唯一索引）、`GET/POST/PUT/DELETE /v2/users/...`、`POST /v2/users/batch-delete`（**仅显式 userIds**，禁止按查询条件批量删）。
- 查询对象 `UserV2PageQuery`（POJO，`@ModelAttribute` 绑定；record 无法被 @ModelAttribute 绑定）、领域规格 `domain/user/UserPageQuery`、仓储 `UserRepository.pageFind(spec, visibleUserIds)`。
- 筛选语义：精确字段 `eq`（userId/employeeNo/employmentStatus/accountStatus/accessAllowed/sourceType…）；模糊字段 `like`（keyword 跨 userId/realName/employeeNo、realName、mobile、email、intranetEmail、jobTitle）；角色 `roleCodes` 走 `sys_user_role` 子查询；创建时间 `createdStart/createdEnd`（end 闭区间，仅日期时次日零点）。
- 排序白名单 `SORT_COLUMNS`（未知/空回退 `u.id`；`Map.of` 的不可变 Map 对 null key 的 get 抛 NPE，必须先判空）。
- 读取范围下推：`UserReadScopeService.resolveVisibleUserIds(operator, snapshot, perms)` 返回 null=全量（`USER_READ`）、否则本人+下级树 ID 集注入 SQL `IN`；分页必须在可见集过滤后做。
- 复杂动态 SQL 在 `resources/mapper/UserMapper.xml`（`selectV2UserPage`，全参数化 foreach，禁止 inSql 拼串）；分页由 `PaginationInnerInterceptor`（`MybatisPlusConfig`）自动 count+limit。

### 部门规则筛选（department_rules）

- 参数 `department_rules`（JSON 数组），契约对齐 lumen_flow 模型日志页：`{ mode: include|exclude, deptCode, treeScope: exact|subtree, membershipScope: any|primary|part_time }`，另有 `{ mode: exclude, unassigned: true }`；上限 20 条。
- 解析：`interfaces/v2/user/UserV2DepartmentRuleResolver`（校验枚举、上限、subtree 用 `sys_department.ancestor_path` 前缀展开含下级）。
- SQL 语义：include 组内 OR、exclude 组整体 NOT、两组 AND；归属匹配主部门 `sys_user.dept_code` + 兼职 `sys_user_part_time_department` EXISTS；未归属 = 无主部门且无兼职。

## 认证与安全

- 登录链路：`AuthApplicationService.login` → 按 userId/工号查本地用户 → `UserAccessPolicy.canAuthenticate` → **LDAP 校验密码**（`LdapDirectoryService`）→ 角色码 → 审计日志 → 签发 JWT。
- 凭证通道：`BearerAuthenticationFilter` 区分 JWT 与 `idm_pat_` 个人访问令牌（PAT）；PAT 经 `PersonalAccessTokenRequestPolicy` 路径放行（V1/V2 路径均已覆盖，禁 self-password/PAT 管理/self-menu）。
- LDAP 模式：`app.ldap.mode` = `stub`（默认，`matchIfMissing`）/ `spring`，`LdapConfiguration` 条件装配；OU 结构 people/groups。
- 密码：默认重置 `123456`；密码重置限流（用户名冷却 + IP 窗口）。

## RBAC 与角色组

- 模型：用户↔角色 N:N，角色↔权限 N:N，角色↔菜单 N:N；Casbin 内存缓存 role→permission `GRANT`，DB 为权威，假阴性时 `refresh()` 重试，事务提交后刷新。
- 不变量：内置角色（`builtIn=1`）权限级别/状态/删除锁定（`BUILT_IN_*_LOCKED`）；`SUPER_ADMIN` 权限系统管理（`SUPER_ADMIN_PERMISSIONS_SYSTEM_MANAGED`）；`admin` 账号不可移除 `SUPER_ADMIN`；并发快照冲突用 `expectedRoleIds`/`expectedPermissionIds` 检 `*_ASSIGN_CONFLICT`(409)。
- 角色组（`interfaces/v2/rolegroup`）：组/协作者/组内角色/成员；角色供应（`/api/v2/open/role-supply` snapshot/changes，PAT 鉴权）供第三方增量取角色成员（含成员平台用户ID `userId`，用于唯一标识匹配）。

## 同步与导入

- LDAP 协调：`SyncApplicationService` 编排 batch/job/diff，`LdapReconcile{Department,User,Membership}Handler` 以 MySQL 为源对比 LDAP 生成差异（MISSING_IN_LDAP/DN/STATUS/FIELD/MISSING_IN_MYSQL），可 autoRepair。
- 飞书导入：`application/sync/importplan`（ImportBatch/ChangeItem 创建/更新/离职 → MySQL+LDAP 双写，含系统管理员保护、领导角色推导、NORMAL_USER 基线、冲突解决、回滚）。
- 关键不变量：系统管理员（admin/SUPER_ADMIN）不可被导入变更/删除；离职同步禁用 LDAP 账号并移出组。

## 前端架构

- 入口 `main.ts`（Element Plus + Pinia + Vue Query + 表格滚动协调器）；路由静态声明 + `menuStore.canAccess` 动态菜单控制；`request.ts` 统一 Axios（Bearer、V1/V2 信封解包、401 清会话跳登录）。
- 状态：`stores/auth`（token/用户/权限码 `can()`/`canAny()`）、`stores/menu`。
- 用户管理筛选组件：`components/admin/user-filter/`（`UserQuickFilterBar` 快速筛选、`UserAdvancedFilterPanel` 精确折叠面板、`UserDepartmentRulesEditor` 部门规则编辑器、`UserActiveFilterTags` 已选条件标签）；筛选状态组件内 `reactive`（不做 route.query），**已选条件标签读 `appliedFilters`（点查询后的快照），不读实时表单**。
- 关键陷阱：`structuredClone` 无法克隆 Vue `reactive` 代理（抛 DataCloneError），必须先 `toRaw()` 解包；筛选回车查询依赖表单 `@submit.prevent` + `native-type="submit"`（精确面板含隐藏提交按钮）。

## 部署与配置

- 环境变量为主配置（JWT 密钥、DB、LDAP、飞书、PAT 限额、SQL 日志脱敏关键字 `mask-keywords`、密码重置限流、同步调度）。
- 默认 profile `dev`；容器内后端走 docker 网络 `corp-idm-mysql:3306`（宿主机 127.0.0.1:3307 不保证从宿主机可连）。
- 启动校验 `EnvironmentStartupVerifier`（default 关闭）；健康 `actuator/health`。
- 版本追踪：`build-versions.txt` 记录每次打包的 commit/JAR/DB 版本。

## 已知限制与技术风险

- **前端 `vue-tsc -b` 有约 40 处既有类型错误**（6 个视图的 el-table 槽位 `DefaultRow`，集中在 profile/role-group/role/sync/system-import 及已修复的 UserListView），阻塞 `npm run build`；镜像构建暂用 `build:no-check`（仅 `vite build`）。待独立清理任务修复。
- `build-with-version-tracking.ps1` 脚本存在引号语法错误，需先修复才能用脚本打包。
- 读取范围（仅下属树操作者）在分页下以内存快照算可见集后注入 SQL，规模扩大后需评估快照成本。
- `resolvePermissionLevel`/部分 enrich 存在按用户 N+1 查询（页面场景可接受，量级增大需批量）。
- 部门规则 subtree 用 `ancestor_path` 前缀展开，部门树调整需维护路径正确性。
