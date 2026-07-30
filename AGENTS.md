# AI Agent 协作规则

本文件记录 Claude/其他 AI 助手在协助开发时必须遵守的规则。

---

## 版本打包追踪规则

### 规则描述

**每次执行后端编译打包命令时（`mvn clean package`），必须自动记录版本信息。**

每次修改的内容涉及 API 时，必须保证原有的 API 对应的原有字段返回内容格式内容完全一致，以兼容其他接入 LDAP 的工具，如果必须修改，请先和用户提及与讨论。

### 执行时机

当执行以下任一命令时触发： 

- `.tools\apache-maven-3.9.6\bin\mvn.cmd clean package -DskipTests`
- `.tools\apache-maven-3.9.6\bin\mvn.cmd clean package`
- `mvn clean package` (任何变体)

### 记录内容

向 `E:\ldap\build-versions.txt` 文件追加一行记录，格式如下：

```
[时间戳] commit: <完整Git提交哈希> | jar: <JAR文件名> | db: <最新Flyway版本>
```

**字段说明：**

- **时间戳**：格式 `YYYY-MM-DD HH:MM:SS`
- **commit**：当前 Git HEAD 的完整 SHA-1 哈希（40字符）
- **jar**：生成的 JAR 包文件名（在 `target/` 目录下）
- **db**：最新的 Flyway 迁移脚本版本号（如 `V36`）

### 实现方式

```powershell
# 打包完成后立即执行
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$commit = git rev-parse HEAD
$jarFile = "corp-idm-platform-0.1.0-SNAPSHOT.jar"
$dbVersion = (Get-ChildItem src/main/resources/db/migration/*.sql | Sort-Object Name -Descending | Select-Object -First 1).BaseName -replace '^(V\d+)__.*','$1'

$record = "[$timestamp] commit: $commit | jar: $jarFile | db: $dbVersion"
Add-Content -Path "build-versions.txt" -Value $record
```

### 示例记录

```
[2026-07-30 16:30:45] commit: 343c934a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q | jar: corp-idm-platform-0.1.0-SNAPSHOT.jar | db: V36
[2026-08-05 10:15:22] commit: 456d789e0f1g2h3i4j5k6l7m8n9o0p1q2r3s4t5u | jar: corp-idm-platform-0.1.0-SNAPSHOT.jar | db: V37
```

### 用途

- **追溯代码版本**：内网部署的 JAR 包可以通过文件名找到对应的 Git 提交
- **数据库版本匹配**：确认 JAR 包内包含哪个版本的数据库迁移脚本
- **回滚依据**：出问题时可以快速定位上一个稳定版本
- **审计追踪**：记录所有打包历史

---

## 其他协作规则

（待补充）
