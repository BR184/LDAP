# 模块化权限套件、角色能力感知与内置角色保护设计

## 一、背景与目标

当前角色授权页面只有少量扁平权限套件，套件之间跨业务模块混合权限，无法支持管理员按“这个人负责用户管理”“这个人负责角色组管理”的方式快速授权。同时，页面没有根据当前账号实际权限隐藏写操作，只读账号进入页面后仍会看到新增、编辑、删除按钮并触发 403。角色组权限还是粗粒度管理权限，无法提供只读套件；系统内置角色也可以被修改作用域、禁用或删除，存在破坏平台基础授权模型的风险。

本次改造建立唯一的权威模型：

1. 权限套件按业务模块隔离，每个模块固定提供“常规操作”和“完整管理”两个等级。
2. 完整管理是对应常规操作的严格超集；套件只是快速选择入口，细粒度权限仍是最终授权事实。
3. 前端根据当前会话的有效权限展示功能，常规操作账号不会看到无权执行的写操作。
4. 角色组查询和管理权限分离，使角色组模块能形成两个有实际差异的等级。
5. 所有内置角色使用 `builtIn` 作为结构锁的权威标记，后端禁止危险的结构变更。
6. 角色组导航卡片使用稳定的信息层级，创建者与访问身份不再互相挤压。

本设计不改变 LDAP 登录、LDAP 属性、现有用户/部门 API 返回字段，也不改变已存在权限码的语义。

## 二、权威权限套件模型

### 2.1 模型结构

权限套件继续由版本化资源 `security/role-permission-bundles.json` 定义，不把显示名称和权限组合硬编码到 Vue 或 Java 逻辑中。资源升级为模块化结构：

- `version`：资源版本。
- `categoryId`：稳定模块 ID，不使用中文显示文本作为程序判断条件。
- `categoryName`：模块显示名称。
- `description`：模块职责说明。
- `sort`：模块排序。
- `tier`：仅允许 `STANDARD` 或 `ADMIN`。
- `id`：稳定套件 ID。
- `name`：套件显示名称。
- `permissionCodes`：套件最终包含的完整权限码集合。

`ADMIN` 套件必须显式列出最终权限集合，不使用运行时继承或权限码前缀推断。目录加载时必须校验：

1. 模块 ID 和套件 ID 全局唯一。
2. 每个模块恰好包含一个 `STANDARD` 和一个 `ADMIN` 套件。
3. 权限码存在、启用且在同一套件内不重复。
4. `ADMIN.permissionCodes` 是 `STANDARD.permissionCodes` 的严格超集。
5. 无效资源使应用启动失败，禁止带着不完整套件运行。

### 2.2 API 兼容

`GET /api/v1/permissions/bundles` 保留现有响应列表和以下原字段：

- `id`
- `name`
- `sort`
- `permissionIds`
- `permissionCodes`

只增加 `categoryId`、`categoryName`、`categoryDescription`、`categorySort`、`tier` 字段。已有调用方仍可按原字段解析；新版前端使用新增字段构建模块矩阵。`/api/v1/auth/me` 不作修改。

## 三、模块与套件边界

### 3.1 用户管理

**常规操作：**

- `MENU_VIEW_USER_MANAGEMENT`
- `USER_READ`
- `USER_DETAIL`

**完整管理：**常规操作全部权限，加上：

- `USER_CREATE`
- `USER_UPDATE`
- `USER_ACCESS_UPDATE`
- `USER_DELETE`
- `USER_BATCH_DELETE`
- `USER_PASSWORD_RESET_ALL`
- `USER_ROLE_ASSIGN`
- `USER_SYNC_LDAP`
- `USER_FEISHU_SYNC`

`USER_READ_SELF_AND_SUBORDINATE_TREE`、`USER_PASSWORD_RESET_DIRECT`、`USER_PASSWORD_RESET_TREE` 是数据范围受限的专用权限，继续作为细粒度权限手动配置，不能并入全局用户套件。

### 3.2 部门管理

**常规操作：**

- `MENU_VIEW_GROUP_MANAGEMENT`
- `DEPT_TREE`
- `DEPT_DETAIL`

**完整管理：**常规操作全部权限，加上：

- `DEPT_CREATE`
- `DEPT_UPDATE`
- `DEPT_DELETE`
- `DEPT_FEISHU_SYNC`
- `DEPT_SYNC_LDAP`

### 3.3 角色管理

**常规操作：**

- `MENU_VIEW_ROLE_MANAGEMENT`
- `ROLE_READ`
- `ROLE_DETAIL`

**完整管理：**常规操作全部权限，加上：

- `ROLE_CREATE`
- `ROLE_UPDATE`
- `ROLE_STATUS`
- `ROLE_DELETE`
- `ROLE_BATCH_DELETE`
- `ROLE_SCOPE_MANAGE`

`ROLE_SCOPE_MANAGE` 仍受平台管理员身份的第二重后端约束。拥有权限码但不是平台管理员，不能修改角色作用域。

### 3.4 权限授权

该模块位于角色管理页面内部，因此套件显式包含进入角色页面并选择目标角色所需的只读依赖。

**常规操作：**

- `MENU_VIEW_ROLE_MANAGEMENT`
- `ROLE_READ`
- `ROLE_DETAIL`
- `ROLE_PERMISSION_READ_BINDINGS`
- `PERMISSION_TREE`

**完整管理：**常规操作全部权限，加上：

- `ROLE_PERMISSION_ASSIGN`

### 3.5 角色组管理

新增 `ROLE_GROUP_READ`，仅允许查询当前账号按成员身份或平台身份可见的角色组数据。

**常规操作：**

- `MENU_VIEW_ROLE_GROUP_MANAGEMENT`
- `ROLE_GROUP_READ`

**完整管理：**常规操作全部权限，加上：

- `ROLE_GROUP_MANAGE`
- `ROLE_GROUP_USER_ASSIGN`

现有 `ROLE_GROUP_MANAGE` 语义保持不变，仍代表角色组定义、协作者、组角色和组令牌管理。原先拥有该权限的角色不会因新增只读权限而失效；所有角色组 GET 接口接受 `ROLE_GROUP_READ` 或 `ROLE_GROUP_MANAGE`，写接口继续要求 `ROLE_GROUP_MANAGE`，成员分配同时要求 `ROLE_GROUP_USER_ASSIGN`。服务层现有 OWNER/MANAGER/平台管理员范围校验继续生效。

### 3.6 菜单管理

**常规操作：**

- `MENU_VIEW_MENU_MANAGEMENT`
- `MENU_TREE`
- `MENU_DETAIL`

**完整管理：**常规操作全部权限，加上：

- `MENU_CREATE`
- `MENU_UPDATE`
- `MENU_DELETE`

### 3.7 文件导入

**常规操作：**

- `MENU_VIEW_FILE_IMPORT_MANAGEMENT`
- `IMPORT_PLAN_READ`
- `IMPORT_PLAN_DETAIL`

**完整管理：**常规操作全部权限，加上：

- `IMPORT_PLAN_CREATE`
- `IMPORT_PLAN_CONFIRM`
- `IMPORT_PLAN_EXECUTE`
- `IMPORT_PLAN_ROLLBACK`
- `IMPORT_PLAN_RETRY_LDAP`
- `IMPORT_PLAN_CONFLICT_RESOLVE`
- `IMPORT_PLAN_CONFLICT_MERGE`
- `IMPORT_PLAN_CANCEL`

### 3.8 同步任务

**常规操作：**

- `MENU_VIEW_OPERATION_LOG`
- `SYNC_JOB_LIST`
- `SYNC_BATCH_DETAIL`
- `SYNC_FEISHU_PREVIEW`
- `SYNC_RECONCILE_PREVIEW`

**完整管理：**常规操作全部权限，加上：

- `SYNC_FEISHU_EXECUTE`
- `SYNC_RECONCILE_EXECUTE`
- `SYNC_JOB_RETRY`

### 3.9 LDAP 控制面

**常规操作：**

- `MENU_VIEW_API_MANAGEMENT`
- `LDAP_FRAMEWORK_READ`
- `LDAP_TEMPLATE_READ`

**完整管理：**常规操作全部权限，加上：

- `LDAP_PRECHECK_EXECUTE`

### 3.10 邮件配置

**常规操作：**

- `MENU_VIEW_MAIL_CONFIG_MANAGEMENT`
- `MAIL_CONFIG_READ`

**完整管理：**常规操作全部权限，加上：

- `MAIL_CONFIG_SAVE`
- `MAIL_CONFIG_TEST`

### 3.11 不进入套件的权限

- `AUTH_ME` 是会话基础能力，由角色创建流程保留默认授权，不显示为业务套件。
- 个人中心和个人访问密钥是登录账号的自助能力，不作为平台角色套件。
- 直属/递归下级查询与密码重置属于用户模块的受限数据范围能力，保留在细粒度树中。

## 四、角色授权交互

角色授权弹窗顶部改为模块矩阵。每个模块使用一行稳定布局：模块名称与简短说明在左，右侧使用 Element Plus 分段控件提供“未配置 / 常规操作 / 完整管理”。

交互规则：

1. 选择“常规操作”时加入该模块 STANDARD 权限，并移除仅属于该模块 ADMIN 的权限。
2. 选择“完整管理”时加入该模块 ADMIN 的完整权限集合。
3. 选择“未配置”时移除仅由该模块贡献的权限；多个模块共用的入口或查询权限只要仍被其他已选模块需要就必须保留。
4. 用户在下方细粒度权限树中手动调整后，若不再精确匹配两个等级，该模块显示“自定义”。
5. 权限树继续作为最终精细调整入口，不另建第二套授权数据。
6. 权限等级 1 或 2 的角色继续由现有规则自动获得全部权限，模块矩阵只读展示。

模块矩阵采用安静、紧凑的企业管理界面，不使用大面积装饰卡片、蓝紫渐变或营销文案。桌面端优先，本次不交付移动端适配。

## 五、当前会话能力感知

新增只读接口 `GET /api/v1/auth/capabilities`，返回当前会话的有效 `permissionCodes`。该接口只接受交互式登录会话，不改变登录响应和 `/auth/me` 返回结构。

前端认证状态保存权限码集合，并提供统一的 `can(permissionCode)` / `canAny(...)` 判断。各业务页面遵循以下原则：

- 没有写权限时不渲染对应新增、编辑、删除、执行、同步、授权按钮。
- 查询依赖按需加载。例如用户页面只有在打开用户编辑或角色分配时才加载角色、部门选项，常规用户查询套件不再被迫拥有角色和部门模块权限。
- 后端 `@PreAuthorize` 和服务层业务校验仍是安全边界；前端能力判断只负责可用性，不替代后端授权。
- 登录切换和退出时清空能力缓存，避免跨账号残留。

## 六、内置角色结构锁

`sys_role.built_in = 1` 是内置角色结构锁的唯一权威标记，不增加第二个锁字段或人工开关。

后端禁止对内置角色执行：

1. 修改角色作用域或角色组归属。
2. 修改权限等级。
3. 启用或禁用。
4. 单个删除或批量删除。

角色编码已经不可编辑。角色名称、备注和细粒度权限授权仍允许具备相应权限的管理员维护，以保留企业按实际职责调整内置角色能力的入口。

前端对上述结构字段显示锁定状态并禁用对应控件，操作按钮不渲染或提供明确提示。后端必须返回稳定业务错误码，保证直接调用 API 也不能绕过保护。

## 七、角色组卡片布局

左侧角色组导航项改成三层信息：

1. 第一行：角色组名称；右侧只显示短标签“所有者”“协管员”“平台监管”或“未加入”。
2. 第二行：角色数量与协作者数量，独占整行。
3. 第三行：创建者，独占整行并允许省略提示。

不再显示“访问身份：平台管理员监督”这类长标签。右侧详情头部保留完整身份描述，并继续分别展示创建者和所有者。卡片使用固定网格轨道、`minmax(0, 1fr)` 和明确的溢出规则，任何身份文本都不能挤掉创建者信息。

## 八、数据迁移

新增 Flyway 迁移：

1. 插入 `ROLE_GROUP_READ` 权限，名称为“委派角色组查询”。
2. 将 `ROLE_GROUP_READ` 授予现有拥有 `ROLE_GROUP_MANAGE` 的角色，保证升级后原管理角色仍具备查询能力。
3. 不修改任何现有用户、角色组、角色成员、令牌、LDAP 或导入数据。

迁移只新增权限和授权关系，不改变现有 API 响应字段。

## 九、错误处理与审计

- 内置角色结构操作分别返回明确业务错误码，不使用通用 500。
- 权限套件资源配置错误在启动期失败并指出模块、套件和权限码。
- 角色组只读权限只能访问服务层判定为当前账号可见的数据，不能绕过成员范围。
- 内置角色被拒绝的变更不写成功审计；成功的自定义角色作用域变更沿用现有审计。
- 前端不吞掉后端业务错误，仍由统一请求层展示错误信息。

## 十、测试与验收

### 10.1 后端

1. 目录测试覆盖 10 个模块、每模块两个等级、ID 唯一、权限存在和 ADMIN 严格包含 STANDARD。
2. 套件 API 兼容测试确认原字段仍存在且新增模块字段正确。
3. 角色组控制器测试覆盖只读账号可 GET、不可写；原 `ROLE_GROUP_MANAGE` 账号继续可查询和管理。
4. Flyway 迁移测试或数据库核验确认旧管理角色获得 `ROLE_GROUP_READ`。
5. 内置角色作用域、权限等级、状态、单删和批量删除全部被后端拒绝；自定义角色行为不回归。
6. 能力接口仅返回当前账号有效权限，不泄露其他账号授权。

### 10.2 前端

1. 类型检查覆盖新增套件字段和能力状态。
2. 单元测试覆盖模块等级识别、STANDARD/ADMIN/未配置切换、共享权限保留和自定义状态。
3. 常规操作账号不会触发不必要的跨模块查询。
4. 无写权限时对应按钮不渲染，有权限时保持原操作流程。
5. 角色组短标签和三行布局通过静态结构与样式检查，不执行浏览器、截图或自动化页面验证。

### 10.3 回归与构建

- 后端执行完整测试。
- 前端执行类型检查、单元测试和生产构建。
- 若执行 `mvn clean package`，必须通过 `scripts/build-with-version-tracking.ps1` 打包并记录 `build-versions.txt`。
- 不使用浏览器、截图、Playwright 或任何浏览器自动化。

## 十一、完成标准

满足以下条件才视为完成：

1. 角色授权页面呈现 10 个独立业务模块，每个模块可一键选择常规或完整管理。
2. 任一完整管理套件都包含对应常规套件，且后端启动校验能够阻止错误定义。
3. 仅拥有常规权限的账号能正常使用对应查询页面，不出现无权限写操作或无关 403。
4. 角色组常规套件确实只能查询，完整管理套件可执行原有管理功能。
5. 所有内置角色无法改变作用域、权限等级、状态或被删除。
6. 角色组卡片中的创建者、统计和短身份标签各自稳定显示。
7. 现有 LDAP 和 `/api/v1/auth/me` 等对外契约无变化，全部自动化检查通过。
