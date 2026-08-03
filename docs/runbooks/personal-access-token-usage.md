# 个人访问密钥使用手册

## 适用范围

个人访问密钥用于脚本、自动化任务和受控工具调用统一身份平台 API。密钥权限是创建者当前 API 权限的子集，不替代账号权限，也不改变 LDAP 登录方式、端口或现有 API 字段。

## 创建与保存

1. 网页登录统一身份平台。
2. 打开左侧“访问密钥”。
3. 点击“创建密钥”，填写名称、有效期并勾选 API 权限。
4. 输入当前登录密码完成五分钟二次验证。
5. 创建成功后立即保存完整密钥。完整密钥只显示一次，数据库只保存 SHA-256 摘要。

密钥格式为：

```text
idm_pat_<tokenUid>_<secret>
```

不要将密钥写入 URL、Git 仓库、前端代码、镜像、日志或截图。生产任务应通过部署平台 Secret、受控环境变量或企业密钥管理系统注入。

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

## 权限规则

- 创建时只能勾选本人当前拥有且已启用的 API 权限。
- 创建后不会自动获得账号后续新增的权限。
- 实际权限始终为“创建时勾选权限”和“账号当前权限”的交集。
- 账号权限被回收、角色或权限被禁用后，相关 PAT 权限立即失效。
- 账号被禁止使用、离职或删除后，名下 PAT 立即不能认证。
- 用户查询和密码重置继续受本人、直属下级、递归下级或全量数据范围限制。
- PAT 不能修改或验证密码，不能读取本人菜单，也不能创建、查询或撤销 PAT。

## 状态与撤销

令牌状态包括 `ACTIVE`、`EXPIRED` 和 `REVOKED`。撤销操作立即生效且不可恢复；需要继续使用时必须创建新密钥。最近使用时间和来源 IP 默认每五分钟最多成功更新一次。

## 配置

```text
APP_PAT_MAX_ACTIVE_PER_USER=20
APP_PAT_LAST_USED_WRITE_INTERVAL_SECONDS=300
```

`APP_PAT_MAX_ACTIVE_PER_USER` 控制每个账号同时有效的密钥数量。`APP_PAT_LAST_USED_WRITE_INTERVAL_SECONDS` 控制最近使用信息的写入节流窗口。

## 故障判断

- `401`：密钥格式错误、摘要不匹配、已撤销、已过期，或所属账号已不可登录。
- `403`：密钥已认证，但未勾选所需权限、账号已失去所需权限，或目标数据超出账号的数据范围。
- 创建时报权限范围错误：刷新页面后重新选择权限，账号权限可能已发生变化。
- 创建时报数量达到上限：撤销不再使用的有效密钥后重试。

## 固定负载基准

联网开发机可执行：

```powershell
python scripts/benchmark_personal_access_token.py `
  --base-url http://127.0.0.1:8083 `
  --username admin `
  --iterations 100 `
  --concurrency 10
```

脚本会提示输入密码，自动创建只含 `AUTH_ME` 的一小时临时密钥，分别输出 JWT/PAT 的 P50、P95、MySQL `Questions` 与 `Com_update` 增量和最近使用元数据变化次数，并在结束或异常时撤销临时密钥。脚本不会输出完整 JWT 或 PAT。

### 2026-08-03 开发环境基线

基线环境为本机 Docker、Java 17、MySQL 8.0，直接访问 `http://127.0.0.1:8083`，后端提交为 `1e3d836`。负载固定为每种凭证 100 次请求、10 路并发，SQL 日志保持开启。该数据用于同环境回归比较，不作为生产 SLA。

| 凭证 | P50 | P95 | 平均值 | MySQL Questions | 每请求 Questions | MySQL Updates |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| JWT | 15.48 ms | 19.65 ms | 16.20 ms | 902 | 9.02 | 0 |
| PAT | 14.77 ms | 27.75 ms | 16.34 ms | 1104 | 11.04 | 1 |

PAT 的 `usageMetadataTransitionCount` 为 `1`，与 MySQL 更新增量一致。10 路并发首次使用由进程内门闩合并为一次条件更新，后续请求在 300 秒窗口内不再写入。
