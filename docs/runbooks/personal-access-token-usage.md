# 个人访问密钥使用手册

## 适用范围

个人访问密钥（PAT）用于脚本、自动化任务和受控工具调用统一身份平台 API。密钥始终受所属账号的准入状态、当前权限和有效期约束，不改变 LDAP、登录和既有业务 API 契约。

## 创建密钥

1. 网页登录统一身份平台。
2. 打开左侧“访问密钥”，进入“创建访问密钥”。
3. 填写名称、描述和有效期。
4. 选择“固定权限范围”时，点击“配置权限”打开弹窗；可按低风险/高风险分区全选、按权限组批量选择，或点击组标题展开后单独调整成员权限。
5. 开启“自动赋予新权限”时，密钥使用账号当前全部 API 权限，账号新增 API 权限后自动获得，权限被回收后立即失效。
6. 创建成功后自动返回密钥清单。所有者可在清单中执行“查看”，并反复复制完整密钥。

密钥格式保持不变：

```text
idm_pat_<tokenUid>_<secret>
```

创建不要求输入当前登录密码。经业务确认，数据库同时保存完整密钥明文和 SHA-256 摘要；摘要用于认证，明文用于所有者重复查看。完整密钥不会进入列表、日志、审计详情、异常或前端持久化存储。

## API 调用

Linux：

```bash
export IDM_PAT='idm_pat_...'
curl -H "Authorization: Bearer ${IDM_PAT}" \
  http://127.0.0.1:8083/api/v1/auth/me
```

PowerShell：

```powershell
$env:IDM_PAT = 'idm_pat_...'
Invoke-RestMethod `
  -Headers @{ Authorization = "Bearer $env:IDM_PAT" } `
  -Uri 'http://127.0.0.1:8083/api/v1/auth/me'
```

PAT 只接受 `Authorization: Bearer` 请求头，不接受 Cookie、URL 查询参数或请求体传递。

## 生命周期接口

- `GET /api/v1/personal-access-tokens`：所有者清单，永不返回完整密钥或摘要。
- `GET /api/v1/personal-access-tokens/available-permissions`：兼容的平铺 API 权限列表。
- `GET /api/v1/personal-access-tokens/available-permission-groups`：按低/高风险分组返回当前账号拥有的 API 权限。
- `POST /api/v1/personal-access-tokens`：创建密钥，不需要密码验证字段。
- `GET /api/v1/personal-access-tokens/{id}/secret`：所有者网页登录会话重复查看可恢复且有效的密钥，响应禁止缓存。
- `POST /api/v1/personal-access-tokens/{id}/rotations`：生成新密钥并立即使旧密钥失效；权限模式和有效期保持不变。
- `POST /api/v1/personal-access-tokens/{id}/revocations`：撤销并保留元数据与审计记录。
- `DELETE /api/v1/personal-access-tokens/{id}`：事务内物理删除密钥和权限关系；审计记录保留。

以上生命周期接口只允许网页登录会话，PAT 不能调用这些接口，也不能调用密码验证/修改和本人菜单接口。

## 权限规则

- 固定模式的权限是创建时所选 API 权限与账号当时有效 API 权限的子集。
- 固定模式后续不会自动扩张；账号权限被回收时相关 PAT 权限立即失效。
- 自动跟随模式每次请求读取账号当前有效 API 权限，新增权限自动生效，回收权限立即失效。
- `MENU` 权限不属于 PAT 范围。
- 账号被禁止使用、离职或删除后，名下 PAT 立即不能认证。
- 用户查询和密码重置仍受本人、直属下级、递归下级或全量数据范围限制。

## 存储与部署

个人访问密钥不需要额外的加密主密钥或环境变量。部署配置只保留数量和最近使用时间写入节流参数：

```text
APP_PAT_MAX_ACTIVE_PER_USER=20
APP_PAT_LAST_USED_WRITE_INTERVAL_SECONDS=300
```

数据库 `secret_value` 保存完整密钥，因此数据库导出和备份也包含可直接调用 API 的凭证。备份文件应沿用数据库现有访问控制，不应进入源码仓库、日志或普通文件共享目录。

## 历史令牌迁移

V47 为旧记录补充 `scope_mode=FIXED`、描述和旧加密字段。V49 删除旧加密字段并新增 `secret_value`；旧记录没有可恢复的完整密钥，清单中的 `secretRecoverable=false`，只能删除或轮换。轮换后会生成并保存新的完整密钥。

V48 按稳定 `permission_code` 确定性修复权限名称和备注，禁止使用猜测式字符集转换。JDBC 和 Flyway 已强制 UTF-8/utf8mb4，启动校验会拒绝错误的 MySQL 会话字符集。

## 故障判断

- `401`：密钥格式错误、摘要不匹配、已撤销、已过期，或所属账号已不可登录。
- `403`：密钥已认证但没有所需 API 权限，或目标数据超出账号的数据范围。
- `PAT_SECRET_UNRECOVERABLE`：历史密钥没有保存完整值，请执行轮换。
- 创建时报权限范围错误：刷新页面后重新选择，账号权限可能已发生变化。
- 创建时报数量达到上限：撤销或删除不再使用的有效密钥后重试。

## 固定负载基准

联网开发机可执行：

```powershell
python scripts/benchmark_personal_access_token.py `
  --base-url http://127.0.0.1:8083 `
  --username admin `
  --iterations 100 `
  --concurrency 10
```

脚本会提示输入密码，自动创建只含 `AUTH_ME` 的一小时固定模式临时密钥，分别输出 JWT/PAT 的 P50、P95、MySQL `Questions` 与 `Com_update` 增量，结束或异常时物理删除临时密钥。脚本不会输出完整 JWT 或 PAT。
