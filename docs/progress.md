## progress.md 文档总则

> 本文档为项目的进度状态权威记录。AGENTS.md 中关于文档职责、更新触发条件和事实优先级的规则同样适用于本文件。
> AI 读取本文件时应将其中内容视为当前工作状态的事实；选择下一步任务、评估阻塞或引用历史结论时，必须以本文为准。本文档与 `product.md`、`architecture.md` 职责分离：产品需求与用户承诺写入 product；技术架构、模块边界与实现约束写入 architecture；当前阶段、已完成项、下一步、阻塞项、风险、最近验证证据及仍有现实影响的历史写入 progress，写入时每条开头标注本条内容所属类型，格式为[xxx]。三者不得交叉重复。
> 内容组织以当前阶段为核心，按“当前目标 → 已完成 → 下一步 → 阻塞与风险 → 最近验证 → 有效历史”顺序呈现。已完成项只记录对后续工作有影响的里程碑和已验证成果，不逐条复述任务细节。阻塞与风险只列当前存在且需关注的真实问题。验证证据须是可复现的测试结果、基准数据或运行截图，禁止引用推测或未经验证的说法。有效历史仅保留对当前决策仍有约束力的过往结论，并明确标注状态与现实影响，过时条目应及时删除。
> 更新触发：当前阶段变更、任一已完成项或下一步发生实质变化、新增或解除阻塞项、验证结果推翻先前结论、或有效历史条目失效时。临时任务、中间调试、重复性工作或已失去现实影响的流水账不得写入。
> 保持行文紧凑，以最小 token 传达当前状态的完整约束。所有陈述必须直接指导下一项工作决策，否则不得保留。

---

# 项目进度

## 当前目标 [目标]

- 分支 `ldap内网上线优化` 的 V2 API 规范化 + 用户管理筛选升级已上线；下一阶段收敛：修复前端既有类型债、推进 lumen_flow 切换 V2、评审合并回 `main`。

## 已完成 [完成]

- **V2 API 规范化**：`/api/v2/**` 全套接口（16 模块：auth/users/departments/roles/menus/permissions/role-groups/role-governance/role-supply/role-supply-tokens/personal-role-context/sync/import/ldap/mail-config/personal-access-tokens）；`ApiResponseV2`(traceId)、`PageResult` 分页、精确/模糊分离、服务端分页、参数校验；V1 全部标注 `@Deprecated` 作兼容层。
- **V1 userId 精确修复**：`GET /api/v1/users?userId=` 改精确匹配、模糊迁移到 `keyword`；修复 lumen_flow 目录同步因子串模糊命中 `lixinran`/`lixinran2` 而歧义卡死的问题。
- **V2 Users 旗舰**：组合筛选 + 服务端分页 + 读取范围下推 SQL + `/by-user-id/{userId}` 精确身份查询 + `department_rules` 部门规则（include/exclude/subtree/主兼职归属/未归属）。
- **前端筛选升级**：快速筛选栏 + 精确筛选折叠面板 + 部门规则编辑器（复刻 lumen_flow）+ 已选条件标签 + 服务端分页；前端 API 模块迁移 `/v2/**`。
- **UI/交互修复**：筛选不生效根因（`structuredClone` 不能克隆 Vue reactive，改 `toRaw`）、筛选回车触发查询、已选条件仅在应用后显示、登录页帮助弹层过大溢出、部门规则编辑器删除按钮溢出/规则名消歧/同级排序、部门树选择。
- **测试**：后端全量 170 通过（含 UserV2ControllerTest 4 项、MyBatis XML 端到端 6 项）；前端 `vue-tsc` 对本次改动文件零错误。
- **部署**：后端容器 `corp-idm-backend-dev`（新 JAR）+ 前端容器 `corp-idm-web`（新镜像）已运行；`build-versions.txt` 记录 V2/修复构建版本。
- **角色供应成员身份标识（V56）**：订阅消息与 `/changes`、`/snapshot` 新增成员平台用户ID（`userId` / `memberUserIds`，飞书ID，与 LDAP `uid`、平台登录名同链）；变更记录表增 `member_user_id` 快照列并按用户表回填；V1/V2 加法兼容，角色组管理与既有消费方零影响；待 IDM 升级部署后与制品平台联测。
- **登录失效与陈旧缓存治理**：将 `LoginView.vue` 登录成功跳转从前端路由 `router.replace` 改为 `window.location.replace` 全页面硬重载，彻底销毁内存中挂载的旧版本 SPA 组件与 TanStack Query 缓存；在 Nginx 配置中为 `/` 和 `/index.html` 增加 `no-store, no-cache, must-revalidate` 严格防缓存响应头；将后端 JWT Token 默认有效期从 30 分钟延长至 480 分钟（8小时）。
- **角色组多平台接入与CAT角色解绑隔离（V57）**：新建专属“CAT监控平台”角色组，将原误配为 `GLOBAL` 作用域的 `CAT_ADMIN` 角色收敛为该角色组下的专属组角色（`role_scope = 'GROUP'`），彻底解决新建其他角色组时全局广播注入 CAT 管理员的问题；修复 `RoleDO` 中 `roleGroupId` 切换作用域时无法更新置空为 NULL 的 MyBatis-Plus 策略缺陷（添加 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`）；补充作用域切换置空单测并通过验证。

## 下一步 [下一步]

1. 修复前端 `vue-tsc -b` 既有类型债（约 40 处 el-table 槽位 `DefaultRow`），使 `npm run build` 恢复绿色，可移除 `build:no-check` 回退。
2. lumen_flow 目录同步切换到 V2 精确端点 `GET /api/v2/users/by-user-id/{userId}`（当前走 V1 `?userId=` 已可用，切换需下游配合）；建议同时加固 lumen_flow 侧（`FetchUser` 精确过滤 + 歧义改永久失败不卡 run）。
3. 修复 `build-with-version-tracking.ps1` 引号语法错误（当前需手动 mvn + 手写版本记录）。
4. 评审分支 `ldap内网上线优化` 并 MR 合并回 `main`。

## 阻塞与风险 [风险]

- **前端类型债**：`vue-tsc -b` 报约 40 处既有错误（profile/role-group/role/sync/system-import 视图的 el-table 槽位），阻塞标准 `npm run build`；镜像构建用 `build:no-check` 绕行。属历史存量，非本次改动引入。
- **本机环境限制**：宿主机 `127.0.0.1:3307` 直连 MySQL 不可用（Communications link failure）；后端须在 docker 网络内经 `corp-idm-mysql:3306` 连接，本机完整 Spring 启动验证依赖容器环境。
- **V1 兼容契约**：V1 接口对外部第三方（lumen_flow/open_webui 等）是真实契约，修改必须与用户确认（见 AGENTS.md）。

## 最近验证 [验证]

- `mvn test`：206 项全绿（0 failure/0 error），含角色治理作用域切换置空与原有角色供应/RBAC全套用例。
- 数据库与运行验证：Flyway V57 成功执行，`sys_role` 中 `CAT_ADMIN` 规范化为 `GROUP` 且绑定组 11（CAT监控平台）；调用 API 验证新建其他角色组（如“GitLab接入平台”）初始角色列表纯净，不再包含 `CAT_ADMIN`。
- 前端 `npm run build`（`vue-tsc -b && vite build`）：构建全绿，0 错误；Vitest 单元测试全部通过。
- 运行验证：后端 `/actuator/health` UP；HTTP 5173 验证 `Cache-Control: no-cache, no-store, must-revalidate`、`Pragma: no-cache`、`Expires: 0` 严格生效；前端 `LoginView` 产物已包含 `window.location.replace`。
- 手工验收：职务/部门/关键词筛选生效、回车查询、已选条件仅在应用后显示、登录帮助弹层完整显示。

## 有效历史 [历史]

- **2026-04 GitLab 真实 LDAP 联调**（见 `docs/progress/progress-028-gitlab-real-ldap-joint-test.md`）：平台 LDAP 框架/模板/预检 + GitLab 登录联调打通；发现 GitLab `user_filter` 重复嵌套问题并修正。状态：已收口，结论仍约束 GitLab 模板的 `user_filter` 输出。
- **V1→V2 演进决策**：`/api/v1/**` 保留为兼容层（契约稳定），新功能走 `/api/v2/**`；该决策仍有效并约束所有对外接口改动。
- **旧编号进度/计划文档**：`docs/progress/` 下 progress-001~028 与 `docs/Design/` 为只读历史，不再作为当前事实来源；当前事实以本文件与 `docs/product.md`、`docs/architecture.md` 为准。
