# 用户准入、导入审查与表格滚动设计

## 1. 背景与目标

当前用户表中的 `status` 同时承载文件人员状态、平台登录权限和 LDAP 启停状态。管理员为了排除不需要使用统一身份平台的员工，只能删除或反复禁用账号；下一次完整文件导入又可能重新创建或启用这些账号，造成重复劳动和遗漏风险。

本次改造建立四个权威模型：

1. 人员文件只管理组织与人员资料。
2. `accessAllowed` 是平台本地准入策略，只能由管理员修改。
3. 兼职部门使用关系表保存，不再使用逗号文本。
4. 表格横向滚动由一个公共组件统一管理。

同时，导入计划审查只展示用户可理解的部门名称和层级路径，不展示 `deptCode`、`partTimeDeptCodes` 等内部字段。

## 2. 设计原则

- 文件导入、合并、撤回和失败重试不得修改本地准入策略。
- 业务状态必须使用明确语义，删除同时表达多种含义的 `UserStatus`。
- 稳定字段键与显示文本分离；持久化记录稳定 ID，接口返回展示标签。
- 开发期直接迁移到新模型，删除旧字段、旧接口和旧调用者，不保留双轨逻辑。
- 组织关系使用规范化数据结构，禁止继续用分隔字符串模拟多值关系。
- Element Plus 私有 DOM 访问集中在单一适配器中，业务页面不感知其内部结构。

## 3. 用户状态权威模型

### 3.1 三个独立维度

| 字段 | 权威来源 | 文件导入可修改 | 业务含义 |
| --- | --- | --- | --- |
| `employmentStatus` | 人员文件 | 是 | 在职或离职 |
| `accountStatus` | 人员文件 | 是 | 来源文件中的原始账号状态文本 |
| `accessAllowed` | 平台管理员 | 否 | 是否获准使用统一身份平台和 LDAP 登录 |

有效登录条件为：

```text
accessAllowed == true AND employmentStatus == ACTIVE
```

`accessAllowed` 不随人员文件变化。离职只改变 `employmentStatus` 和组织关系；它可以让有效登录条件不成立，但不能篡改管理员保存的准入决定。员工重新入职时，如果此前被管理员设为不允许使用，仍保持不允许；如果此前允许使用，则恢复为有效登录。

### 3.2 默认值和迁移

- 现有账号的 `accessAllowed` 从当前 `status` 一次性迁移。
- 文件首次创建的新账号默认 `accessAllowed = true`。
- 手工创建账号时由管理员明确选择，默认值为允许。
- 迁移完成后删除数据库 `status`、领域 `UserStatus`、旧状态命令和 `/users/{id}/status` 接口。

### 3.3 管理员操作

- 用户列表新增“允许使用”列，使用 Element Plus 开关。
- 使用独立接口 `PUT /api/v1/users/{id}/access`，请求只包含 `accessAllowed`。
- 使用独立权限 `USER_ACCESS_UPDATE`，只赋予管理员和超级管理员。
- 关闭准入时递增 `tokenVersion`，当前会话立即失效，并将 LDAP 登录状态同步为禁用。
- 开启准入时只有在员工仍为在职状态时启用 LDAP；离职员工保持 LDAP 禁用。
- 操作记录审计日志中的操作者、原值、新值和时间。
- 文件管理用户不允许普通删除；管理员通过准入开关长期排除。手工账号仍按原权限规则删除。

LDAP 同步或对账统一使用有效登录条件，禁止任何导入路径直接按文件字段启用 LDAP。

## 4. 导入模型与审查投影

### 4.1 导入快照边界

`UserImportSnapshot` 只包含导入拥有的数据：稳定用户身份、姓名、工作邮箱、手机号、工号、主部门、兼职部门、职务、直属上级、来源账号文本和在职状态。

以下本地字段从导入快照删除：

- `accessAllowed`
- `tokenVersion`
- `ldapDn`
- 角色与权限绑定
- 内网邮箱的既有值

执行更新时从数据库加载现有用户，将导入拥有字段合并到现有实体；执行创建时由本地创建策略补齐准入、令牌、LDAP 和默认角色。撤回同样只恢复导入拥有字段。

### 4.2 稳定字段键

`sys_import_change_item.field_name` 改为稳定的 `ImportFieldKey`，例如：

- `USER_REAL_NAME`
- `USER_MAIN_DEPARTMENT`
- `USER_PART_TIME_DEPARTMENTS`
- `USER_JOB_TITLE`
- `USER_EMPLOYMENT_STATUS`
- `DEPARTMENT_NAME`
- `DEPARTMENT_PARENT`

Flyway 一次性迁移现有 camelCase 字段名。应用代码只读写新字段键，不保留旧键回退。

### 4.3 部门显示

导入审查层构建批次级部门目录，合并数据库部门与本批次部门前后快照，并输出：

- 字段键
- 中文字段名称
- 格式化后的原值
- 格式化后的新值
- 风险、确认和执行状态

`USER_MAIN_DEPARTMENT` 显示为“部门名称”，值为完整部门层级路径。`USER_PART_TIME_DEPARTMENTS` 显示为“兼职部门”，每个部门路径独占一行。部门无法解析时显示“部门信息缺失”，内部编码仍只保留在审计原始数据中。

用户审查主表的部门列、搜索关键字和变更摘要均使用展示名称，不再使用部门编码。

## 5. 兼职部门数据模型

新增 `sys_user_part_time_department`：

| 字段 | 说明 |
| --- | --- |
| `user_id` | 用户数据库 ID |
| `dept_code` | 部门稳定编码 |
| `sort_no` | 源文件顺序 |
| `gmt_create` | 创建时间 |

主键为 `(user_id, dept_code)`，并对 `dept_code` 建索引。迁移将 `sys_user.part_time_dept_codes` 拆分写入关系表，然后删除旧列。

仓储负责在保存用户时以事务方式同步关系集合；列表查询批量读取关系，避免逐用户查询。领域对象继续使用有序 `partTimeDeptCodes`，持久化细节不泄漏到应用层。

用户接口用 `partTimeDepartments` 对象列表替代 `partTimeDeptCodes` 与 `partTimeDeptNames` 平行数组。每项包含 `deptCode`、`deptName`、`departmentPath`。列表与详情逐项展示完整路径，不使用省略号；编辑表单仍提交稳定编码列表。

文件解析集中支持英文逗号、中文逗号、英文分号、中文分号和换行分隔，多路径有序去重，主部门自动从兼职部门中排除。

## 6. 公共横向滚动条

### 6.1 组件边界

新增：

- `PersistentTableScrollFrame.vue`：页面使用的公共容器。
- `element-table-scroll-adapter.ts`：唯一允许访问 Element Plus 表格滚动 DOM 的适配器。
- `persistent-table-scroll-coordinator.ts`：在同一视口存在多个表格时选择当前活动表格。
- `table-scroll-math.ts`：纯滚动比例与边界计算。
- `.idm-table-scroll-frame`：公共样式入口。

所有横向可能溢出的表格统一包裹公共组件。原生横向滚动条仅在这个组件内隐藏，纵向滚动条不受影响。

### 6.2 行为

- 活动滚动条固定在视口底部安全距离处，并与当前表格可见左右边界对齐。
- 轨道无背景；滑块默认透明浅灰，悬浮和拖动时变为深灰。
- 使用 Pointer Events 与 `setPointerCapture`，按下后鼠标离开滑块仍可继续拖动。
- 拖动期间在根节点添加状态类，禁止页面文字选择并保持抓取光标。
- 使用 `requestAnimationFrame` 合并滚动和尺寸更新；拖动按初始几何关系直接计算，不在每次指针移动时触发布局测量。
- 支持轨道点击、方向键、Home、End 和 ARIA 滚动条语义。
- 分页、列宽、窗口尺寸和表格数据变化时通过 `ResizeObserver` 与适配器重新计算。
- 没有横向溢出、表格不可见或弹窗关闭时隐藏固定滚动条。

### 6.3 兼容基线

前端构建目标固定为 Chrome/Edge 80+、Firefox 78 ESR+、Safari 13.1+。该基线原生支持本方案使用的 Pointer Events、ResizeObserver、requestAnimationFrame 和 CSS 变量。Vue 3 与 Element Plus 不支持 IE，因此不引入 IE 分支或第二套事件实现。

## 7. 错误处理与一致性

- 管理员准入更新采用数据库事务；LDAP 失败不能伪装成功，返回明确错误并保留可重试审计信息。
- JWT 过滤器每次校验有效登录条件和 `tokenVersion`。
- LDAP 对账使用同一有效登录策略，自动修复偏差。
- 部门关系迁移前校验空值、重复值和主部门重复；迁移后以关系表为唯一来源。
- 导入审查投影不得因缺失部门而返回内部编码，使用明确缺失标记。

## 8. 验收标准

- 管理员关闭某员工“允许使用”后，重复导入、冲突合并、撤回和重试均不会重新开启。
- 首次导入的新在职员工默认允许使用。
- 被关闭员工无法登录，既有 JWT 立即失效，LDAP 状态为禁用。
- 导入详情不出现 `deptCode`、`partTimeDeptCodes` 或原始部门编码。
- 主部门和兼职部门均显示完整名称路径，多个兼职部门全部可见。
- 数据库不存在 `sys_user.status` 和 `sys_user.part_time_dept_codes`，应用无旧状态接口和旧字段调用。
- 所有表格页使用同一公共横向滚动条，拖动流畅且不选中背景文字。
- 后端测试、前端单元测试、类型检查和生产构建全部通过。
- 遵守浏览器验证禁令，不调用浏览器、截图或浏览器自动化。
