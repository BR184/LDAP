# LDAP 企业统一身份管理平台

## 项目概述

这是一个企业级 LDAP 统一身份管理平台 (corp-idm-platform)，用于管理企业内部用户、部门、角色、权限，并提供第三方系统（GitLab、Jenkins、Nexus、禅道）的 LDAP 统一认证接入能力。

**核心功能：**

- 用户管理（增删改查、角色分配、密码重置、LDAP/飞书同步）
- 部门管理（树形结构、LDAP/飞书同步）
- 角色与权限管理（RBAC 模型、菜单绑定、权限授权）
- 菜单管理（动态菜单树）
- LDAP 控制面（框架查询、预检）
- 飞书组织架构同步（API 同步 + 文件导入）
- 邮件配置管理
- 同步任务监控

---

## 技术栈

### 后端

- **Java 17** + **Spring Boot 3.3.0**
- **MyBatis-Plus 3.5.7** (ORM)
- **Spring Security** + **JWT** (认证授权)
- **Spring LDAP** (LDAP 目录操作)
- **Casbin** (策略引擎，基于角色的权限控制)
- **Flyway** (数据库迁移)
- **MySQL 8.0** (主数据库) / H2 (测试)
- **Spring Mail** (邮件通知)

### 前端

- **Vue 3.5** + **TypeScript 6.0**
- **Vite 8.0** (构建工具)
- **Element Plus 2.13** (UI 组件库)
- **Pinia 3.0** (状态管理)
- **TanStack Vue Query 5.99** (服务端状态管理)
- **Axios** (HTTP 客户端)
- **Sass** (CSS 预处理)

---

## 项目结构

```
E:\ldap\
├── src/main/java/com/company/idm/    # 后端 Java 源码
│   ├── boot/                         # 启动入口
│   │   └── IdmBootApplication.java   # Spring Boot 主类
│   ├── application/                  # 应用服务层（用例编排）
│   │   ├── auth/                     # 认证（登录、JWT、Token）
│   │   ├── user/                     # 用户用例
│   │   ├── department/               # 部门用例
│   │   ├── rbac/                     # 角色、菜单、权限用例
│   │   ├── ldap/                     # 第三方 LDAP 集成
│   │   ├── mail/                     # 邮件配置
│   │   └── sync/                     # 飞书同步
│   ├── domain/                       # 领域层（实体、仓储接口）
│   │   ├── user/                     # 用户聚合
│   │   ├── department/               # 部门聚合
│   │   └── rbac/                     # 角色、菜单、权限聚合
│   ├── infrastructure/               # 基础设施层
│   │   ├── persistence/              # 持久化（MyBatis Mapper、DO、Repository 实现）
│   │   ├── security/                  # Spring Security 配置
│   │   ├── ldap/                     # LDAP 连接与操作
│   │   ├── casbin/                   # Casbin 策略配置
│   │   ├── config/                   # Spring 配置类
│   │   └── startup/                  # 启动校验
│   ├── interfaces/                   # 接口层（REST Controller、DTO）
│   │   ├── auth/                     # 认证 API
│   │   ├── user/                     # 用户 API
│   │   ├── department/               # 部门 API
│   │   ├── role/                     # 角色 API
│   │   ├── menu/                     # 菜单 API
│   │   ├── permission/               # 权限 API
│   │   ├── ldap/                    # LDAP 控制 API
│   │   ├── sync/                    # 同步任务 API
│   │   └── system/                  # 系统配置 API
│   └── common/                       # 公共组件
│       ├── api/                      # 统一响应包装 ApiResponse<T>
│       ├── enums/                    # 枚举定义
│       ├── exception/                # 业务异常
│       └── log/                      # 日志常量
│
├── src/main/resources/
│   ├── application.yml               # 公共配置
│   ├── application-dev.yml           # 开发环境配置
│   ├── application-local.yml         # 本地配置
│   ├── application-prod.yml          # 生产环境配置
│   ├── application-test.yml          # 测试环境配置
│   ├── db/migration/                 # Flyway 数据库迁移脚本 (V1~V28)
│   └── casbin/                       # Casbin 策略文件
│
├── frontend/                         # Vue 3 前端项目
│   ├── src/
│   │   ├── api/                      # API 层
│   │   │   ├── request.ts            # Axios 实例与拦截器
│   │   │   └── modules/              # 按模块拆分的 API 函数
│   │   │       ├── auth.ts           # 登录、获取当前用户
│   │   │       ├── user.ts           # 用户 CRUD、角色分配
│   │   │       ├── role.ts           # 角色 CRUD、菜单绑定
│   │   │       ├── menu.ts           # 菜单 CRUD
│   │   │       ├── department.ts     # 部门 CRUD
│   │   │       ├── ldap.ts           # LDAP 框架查询
│   │   │       ├── permission.ts     # 权限树
│   │   │       ├── sync.ts           # 同步任务
│   │   │       ├── mail-config.ts    # 邮件配置
│   │   │       └── system-import.ts  # 飞书文件导入
│   │   ├── router/                   # Vue Router 路由配置
│   │   ├── stores/                   # Pinia 状态管理
│   │   │   ├── app.ts                # UI 状态（侧边栏折叠）
│   │   │   ├── auth.ts               # 认证状态（token、用户信息）
│   │   │   └── menu.ts               # 菜单/权限状态
│   │   ├── views/                    # 页面视图
│   │   │   ├── auth/                 # 登录页
│   │   │   ├── dashboard/            # 首页
│   │   │   ├── user/                 # 用户管理
│   │   │   ├── department/           # 部门管理
│   │   │   ├── role/                 # 角色管理
│   │   │   ├── menu/                 # 菜单管理
│   │   │   ├── ldap/                 # LDAP 控制面
│   │   │   ├── sync/                 # 同步任务
│   │   │   ├── system/               # 系统配置
│   │   │   └── profile/              # 个人中心
│   │   ├── layout/                   # 布局组件
│   │   ├── components/               # 公共组件
│   │   ├── styles/                   # 样式（SCSS）
│   │   ├── types/                    # TypeScript 类型定义
│   │   └── constants/                # 常量（导航配置）
│   ├── vite.config.ts                # Vite 配置
│   ├── tsconfig.json                 # TypeScript 配置
│   └── package.json                  # 前端依赖
│
├── deploy/                           # 部署方案
│   ├── docker-compose-dev.yml        # 开发环境（含 GitLab）
│   ├── docker-compose-internal.yml   # 内网生产环境
│   ├── nginx/default.conf            # Nginx 反向代理配置
│   └── README-ubuntu2204-internal.md # Ubuntu 离线部署指南
│
├── scripts/                          # 开发脚本
│   ├── start-backend-dev.ps1         # 启动后端开发容器
│   ├── start-dev-with-mail.ps1       # 带邮件配置启动
│   └── pinyin-helper/                # 拼音转换工具
│
├── docs/                             # 项目文档
│   ├── Design/                       # 设计说明书
│   ├── checklists/                   # 交付清单
│   ├── plans/                        # 开发计划
│   ├── progress/                     # 进度记录
│   ├── runbooks/                     # 运行手册
│   ├── templates/                    # 接入模板
│   └── feishu-import/                # 飞书导入数据
│
├── handover-packages/                # 交接包
│   ├── docs-handover.zip
│   ├── runtime-package.zip
│   ├── source-code.zip
│   └── 环境与账号密码交接清单.md     # 关键交接文档
│
├── .tools/                           # 本地开发工具（自包含）
│   ├── jdk-17/                       # OpenJDK 17
│   └── apache-maven-3.9.6/           # Maven 3.9.6
│
├── pom.xml                           # Maven 配置
├── Dockerfile                        # 后端 Docker 镜像
└── README.md                         # 项目说明
```

---

## 开发环境启动

### 前置条件

项目已包含自包含开发工具（`.tools/` 目录），无需额外安装 JDK 和 Maven。

### 1. 启动依赖服务（MySQL、OpenLDAP）

```powershell
cd E:\ldap\deploy
docker compose -f docker-compose-dev.yml up -d
```

服务清单：

- MySQL 8.0: 端口 `3307`，数据库 `corp_idm`，用户/密码 `root/root`
- OpenLDAP: 端口 `389`，域 `corp.local`，管理员密码 `admin`
- GitLab CE: 端口 `8929`（可选）

### 2. 编译后端

```powershell
cd E:\ldap
.tools\apache-maven-3.9.6\bin\mvn.cmd clean package -DskipTests
```

输出: `target/corp-idm-platform-0.1.0-SNAPSHOT.jar`

### 3. 启动后端（Docker 容器方式）

```powershell
# 启动后端容器（连接 docker-compose 网络）
.\scripts\start-backend-dev.ps1

# 或带邮件配置启动（本地 Java 方式）
.\scripts\start-dev-with-mail.ps1
```

后端地址: `http://localhost:8083`

### 4. 启动前端

```powershell
cd E:\ldap\frontend
npm install
npm run dev
```

前端地址: `http://localhost:5173`

### 5. 默认管理员账号

- 用户名: `admin`
- 密码: `admin123`（见 `application-dev.yml`）

---

## 常用命令

### 后端

```powershell
# 编译（跳过测试）
.tools\apache-maven-3.9.6\bin\mvn.cmd clean package -DskipTests

# 运行测试
.tools\apache-maven-3.9.6\bin\mvn.cmd test

# 本地运行（开发配置）
java -jar target/corp-idm-platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

### 版本打包规则（重要）

**每次执行打包操作时，必须记录版本信息到 `build-versions.txt`：**

1. 记录当前 Git 提交哈希（完整 SHA）
2. 记录生成的 JAR 包文件名
3. 记录打包时间戳
4. 记录数据库迁移版本（最新的 Flyway 脚本版本号）

**推荐使用带版本追踪的打包脚本：**
```powershell
# 自动打包并记录版本信息
.\scripts\build-with-version-tracking.ps1

# 或带测试
.\scripts\build-with-version-tracking.ps1 -SkipTests:$false
```

版本记录格式示例（位于 `build-versions.txt`）：
```
[2026-07-30 16:30:45] commit: 343c934a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q | jar: corp-idm-platform-0.1.0-SNAPSHOT.jar | db: V36
```

这样可以追溯内网部署的任何 JAR 包对应的代码版本和数据库版本。

### 前端

```powershell
cd frontend

# 安装依赖
npm install

# 开发模式
npm run dev

# 类型检查
npm run check

# 生产构建
npm run build
```

### Docker

```powershell
# 启动所有服务
cd deploy
docker compose -f docker-compose-dev.yml up -d

# 查看日志
docker compose -f docker-compose-dev.yml logs -f idm-app

# 停止服务
docker compose -f docker-compose-dev.yml down

# 清理数据（危险）
docker compose -f docker-compose-dev.yml down -v
```

---

## 架构设计

### 分层架构

项目采用 DDD（领域驱动设计）分层架构：

```
interfaces (接口层)
    ↓ 调用
application (应用服务层)
    ↓ 编排
domain (领域层)
    ↓ 依赖
infrastructure (基础设施层)
```

- **interfaces**: REST Controller、请求/响应 DTO
- **application**: 用例编排、事务边界、跨聚合协调
- **domain**: 领域实体、仓储接口、业务规则
- **infrastructure**: 仓储实现、外部服务集成、配置

### RBAC 权限模型

```
用户 (User) ── N:N ── 角色 (Role) ── N:N ── 权限 (Permission)
                           │
                           └── N:N ── 菜单 (Menu)
```

- 用户通过角色获得权限
- 角色绑定菜单控制前端导航
- 权限控制 API 访问（Casbin 策略）

### 数据库表

核心表：

- `sys_user` - 用户表
- `sys_department` - 部门表
- `sys_role` - 角色表
- `sys_menu` - 菜单表
- `sys_permission` - 权限表
- `sys_user_role` - 用户角色关联表
- `sys_role_menu` - 角色菜单关联表
- `sys_role_permission` - 角色权限关联表
- `sys_sync_job` / `sys_sync_batch` - 同步任务表
- `sys_audit_log` - 审计日志表

---

## API 接口

### 认证

- `POST /api/v1/auth/login` - 登录
- `GET /api/v1/auth/me` - 获取当前用户信息
- `POST /api/v1/auth/forgot-password` - 忘记密码

### 用户管理

- `GET /api/v1/users` - 用户列表
- `GET /api/v1/users/:id` - 用户详情
- `POST /api/v1/users` - 创建用户
- `PUT /api/v1/users/:id` - 更新用户
- `PUT /api/v1/users/:id/status` - 更新用户状态
- `DELETE /api/v1/users/:id` - 删除用户
- `POST /api/v1/users/:id/reset-password` - 重置密码
- `PUT /api/v1/users/:id/roles` - 分配角色

### 部门管理

- `GET /api/v1/departments/tree` - 部门树
- `GET /api/v1/departments/:id` - 部门详情
- `POST /api/v1/departments` - 创建部门
- `PUT /api/v1/departments/:id` - 更新部门
- `DELETE /api/v1/departments/:id` - 删除部门

### 角色管理

- `GET /api/v1/roles` - 角色列表
- `GET /api/v1/roles/:id` - 角色详情
- `POST /api/v1/roles` - 创建角色
- `PUT /api/v1/roles/:id` - 更新角色
- `DELETE /api/v1/roles/:id` - 删除角色
- `PUT /api/v1/roles/:id/menus` - 绑定菜单
- `PUT /api/v1/roles/:id/permissions` - 授权权限

### 菜单管理

- `GET /api/v1/menus/tree` - 菜单树
- `GET /api/v1/menus/:id` - 菜单详情
- `POST /api/v1/menus` - 创建菜单
- `PUT /api/v1/menus/:id` - 更新菜单
- `DELETE /api/v1/menus/:id` - 删除菜单

---

## 环境配置

### 必填环境变量（生产环境）

| 变量                           | 说明                 | 示例                                     |
| ------------------------------ | -------------------- | ---------------------------------------- |
| `APP_JWT_SECRET`             | JWT 密钥（>=32字符） | `your-256-bit-secret-key-here`         |
| `APP_LDAP_URL`               | LDAP 地址            | `ldap://localhost:389`                 |
| `APP_LDAP_BASE_DN`           | LDAP 基础 DN         | `dc=corp,dc=local`                     |
| `APP_LDAP_ADMIN_DN`          | LDAP 管理员 DN       | `cn=admin,dc=corp,dc=local`            |
| `APP_LDAP_ADMIN_PASSWORD`    | LDAP 管理员密码      | `admin`                                |
| `SPRING_DATASOURCE_URL`      | 数据库连接           | `jdbc:mysql://localhost:3306/corp_idm` |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名         | `root`                                 |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码           | `root`                                 |

### Profile 说明

- `local` - 本地开发（使用内存数据库 H2）
- `dev` - 开发环境（连接 Docker Compose 服务）
- `test` - 测试环境
- `prod` - 生产环境

---


## 文档管理规则

- 项目设计文档**只维护 `docs`文档**。
- 禁止在 `docs/` 下新增编号之外的设计文档或计划文档。需求分析、设计方案、重构计划等内容一律归入对应编号文档的合适章节。
- `CLAUDE.md` 只记录通用共识和规则（技术决策、安全规则、代码规范、协作规则），不记录最新进度口径或其他 已有的信息。
- 文档膨胀比文档缺失更危险：维护不过来的文档会全部腐烂成垃圾信息干扰项目开发。

## 文档索引

- [部署指南](deploy/README-ubuntu2204-internal.md) - Ubuntu 22.04 离线部署
- [生产环境配置](docs/runbooks/production-environment-configuration.md) - 环境变量详细说明
- [GitLab LDAP 联调](docs/runbooks/gitlab-ldap-joint-debug.md) - GitLab 集成步骤
- [交接清单](handover-packages/环境与账号密码交接清单.md) - 环境与账号信息

---

## 注意事项

1. **密码策略**: 生产环境必须修改默认管理员密码 `admin123`
2. **JWT 密钥**: 生产环境必须配置强随机密钥，禁止使用默认值
3. **数据库迁移**: Flyway 自动执行，生产部署前检查 `db/migration` 脚本
4. **LDAP 模式**: 支持 `stub`（本地体验）和 `spring`（真实 LDAP）两种模式
5. **飞书同步**: 需配置飞书开放平台 App ID 和 Secret

---

## 常见问题

### Q: 后端启动失败，报数据库连接错误？

A: 确保已启动 MySQL 容器（`docker compose -f docker-compose-dev.yml up -d`），或使用 `local` profile 启动（使用 H2 内存数据库）。

### Q: 前端 API 请求 401？

A: 检查 token 是否过期，或重新登录。开发环境可以清除 localStorage 中的 `idm-access-token`。

### Q: LDAP 同步失败？

A: 检查 LDAP 服务状态和配置，确保 `APP_LDAP_*` 环境变量正确。可使用 LDAP Admin 工具验证连接。

### Q: 飞书同步失败？

A: 检查飞书开放平台配置，确保 App ID 和 Secret 正确，IP 白名单已配置。
