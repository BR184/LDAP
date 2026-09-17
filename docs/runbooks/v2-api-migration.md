# V2 API 规范与 V1→V2 迁移说明

> 记录 V2 API 的规范约定、V1→V2 端点映射与迁移状态。
> 遵循阿里巴巴 Java 开发手册：统一响应体、服务端分页、精确/模糊分离、参数校验、资源化命名。

## 背景

- 事故：`GET /api/v1/users?userId=` 对 userId 做子串模糊匹配，`lixinran` 命中 `lixinran2`，导致下游身份目录（lumen_flow）同步歧义卡死。
- 决策：API 升级为 `/api/v2/**`（V2 为标准），`/api/v1/**` 全部保留并标注 `@Deprecated`，仅供存量调用方与第三方兼容。
- V1 `GET /api/v1/users` 的 `userId` 参数改为**精确匹配**；模糊搜索迁移到新增 `keyword` 参数。

## V2 通用规范

| 项 | 约定 |
|---|---|
| 统一响应体 | `ApiResponseV2 { success, code, message, data, traceId }`；错误码字符串三段式（复用 `ErrorCodeConstants`） |
| 分页 | 入参 `pageNum`(≥1)/`pageSize`(1~100)；出参 `PageResult { items, total, pageNum, pageSize }`；一律服务端分页 |
| 精确/模糊分离 | 精确字段 `eq`（走唯一索引）；模糊字段 `like`；列表综合搜索用 `keyword` |
| 资源命名 | 资源复数（`/users`、`/role-groups`）；子资源表达层级；状态/操作变更用子资源 + 明确 HTTP 方法 |
| 鉴权 | 沿用 Casbin 权限码（与 URL 无关，V2 复用同批权限码） |
| 读取范围 | `USER_READ` 全量；`USER_READ_SELF_AND_SUBORDINATE_TREE` 下推 SQL（本人+下级树）后再分页 |
| 批次删除 | V2 收紧为仅显式 ID 数组，禁止按查询条件批量删 |

## 部门规则（department_rules）

`GET /api/v2/users?department_rules=<json>`，JSON 数组结构（对齐 lumen_flow 模型日志页）：

```json
[
  { "mode": "include", "deptCode": "D001", "treeScope": "subtree", "membershipScope": "any" },
  { "mode": "exclude", "deptCode": "D002", "treeScope": "exact", "membershipScope": "part_time" },
  { "mode": "exclude", "unassigned": true }
]
```

- `mode`: include/exclude（include 组 OR、exclude 组整体 NOT、两组 AND）
- `treeScope`: exact / subtree（subtree 用 `sys_department.ancestor_path` 前缀展开含下级）
- `membershipScope`: any / primary / part_time（主部门 `sys_user.dept_code`、兼职 `sys_user_part_time_department`）
- `unassigned`: 未归属部门特殊规则（无主部门且无兼职）；上限 20 条

## V1→V2 端点映射

| V1（弃用，仅供兼容） | V2（标准） | 说明 |
|---|---|---|
| `GET /api/v1/users?userId=&keyword=` | `GET /api/v2/users?keyword=&...` | V2 组合筛选+服务端分页；V1 `userId` 已改精确 |
| — | `GET /api/v2/users/by-user-id/{userId}` | 精确身份查询（走唯一索引），第三方目录用 |
| `GET/POST/PUT/DELETE /api/v1/users/...` | `/api/v2/users/...` | 纯路径迁移 |
| `POST /api/v1/users/batch-delete` | `POST /api/v2/users/batch-delete` | 仅显式 `userIds`，禁止按条件批量删 |
| `/api/v1/auth/*` | `/api/v2/auth/*` | 纯迁移 |
| `/api/v1/departments/*` | `/api/v2/departments/*` | 纯迁移 |
| `/api/v1/roles/*` | `/api/v2/roles/*` | 批量删除收紧为仅显式 `roleIds` |
| `/api/v1/menus/*` | `/api/v2/menus/*` | 纯迁移 |
| `/api/v1/permissions/*` | `/api/v2/permissions/*` | 纯迁移 |
| `/api/v1/role-groups/*` | `/api/v2/role-groups/*` | 纯迁移 |
| `/api/v1/role-governance/roles/*` | `/api/v2/role-governance/roles/*` | 纯迁移 |
| `/api/v1/open/role-supply/*` | `/api/v2/open/role-supply/*` | 第三方契约，参数/游标不变 |
| `/api/v1/role-supply-tokens/*`、`/api/v1/role-groups/*/tokens/*` | `/api/v2/role-groups/{groupId}/subscriptions/**`、`/api/v2/role-supply-subscriptions/global/**` | 令牌管理已被订阅管理取代（V54），凭证随订阅自动生成 |
| `/api/v1/users/me/role-context` | `/api/v2/users/me/role-context` | 纯迁移 |
| `/api/v1/sync/*` | `/api/v2/sync/*` | 纯迁移 |
| `/api/v1/import/*` | `/api/v2/import/*` | 动作类接口幂等/回滚语义复核后迁移 |
| `/api/v1/ldap/*` | `/api/v2/ldap/*` | 纯迁移 |
| `/api/v1/system/mail-config/*` | `/api/v2/system/mail-config/*` | 纯迁移 |
| `/api/v1/personal-access-tokens/*` | `/api/v2/personal-access-tokens/*` | 纯迁移；PAT 请求策略已放行 v2 |

## 迁移状态

- [x] 后端 V2 控制器（auth/users/departments/roles/menus/permissions/role-groups/role-governance/role-supply/role-supply-subscriptions/personal-role-context/sync/import/ldap/mail-config/personal-access-tokens）
- [x] V1 全部控制器标注 `@Deprecated`
- [x] 前端用户模块切换 V2（筛选升级 + 服务端分页）
- [x] 前端其余 API 模块路径切 V2
- [x] V1 `users?userId` 精确修复（配合 `keyword` 模糊）
- [x] `GET /api/v2/users/by-user-id/{userId}` 精确身份查询
- [ ] lumen_flow 目录同步切换到 V2 精确端点（待下游配合）
- [ ] open_webui 可选切换 V2（当前已免疫，无需强制）
