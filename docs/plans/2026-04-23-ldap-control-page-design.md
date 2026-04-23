# LDAP 控制面前端页设计方案

## 1. 背景

当前后端已经具备第三方 LDAP 接入控制面的核心接口：

- `GET /api/v1/ldap/framework`
- `GET /api/v1/ldap/templates/{systemCode}`
- `POST /api/v1/ldap/precheck`

结合当前阶段诉求，本次前端不实现各系统模板查看页，只实现“框架说明”和“联调预检”两块核心能力。同时，原有左侧导航和首页快捷入口中的“权限树”入口移除。

## 2. 目标

- 移除左侧导航与首页快捷入口中的“权限树”
- 新增 LDAP 控制面页面
- 展示统一 LDAP 接入框架说明
- 支持执行 LDAP 联调预检并展示结果
- 不实现 GitLab / Jenkins / Nexus / 禅道模板查看页

## 3. 非目标

- 不实现系统模板查看页
- 不实现 LDAP 预检结果导出
- 不实现独立的批次/历史记录页

## 4. 页面结构

LDAP 控制面页采用“说明卡片 + 预检表单 + 结果卡片”的单页布局：

- 框架说明卡片
  - 运行模式
  - Base DN
  - 用户目录
  - 分组目录
  - 登录字段
  - 用户过滤条件
  - 授权模式
  - 支持系统列表
- 联调预检表单
  - 系统类型（可选）
  - 启用用户样本（必填）
  - 禁用用户样本（可选）
- 预检结果区
  - 总体状态
  - 当前使用的过滤条件
  - 检查项列表

## 5. 交互设计

- 页面进入后自动拉取框架说明
- 预检表单提交后执行真实预检
- 若系统类型未选择，则按通用预检执行
- 结果列表使用状态标签区分 `PASS / FAIL / SKIPPED`
- 失败提示直接复用后端返回信息

## 6. 导航调整

- 从左侧导航移除“权限树”
- 从首页快捷入口移除“权限树”
- 左侧新增“LDAP 控制面”入口

## 7. 前后端契约

本次前端仅接以下 LDAP 接口：

- `GET /api/v1/ldap/framework`
- `POST /api/v1/ldap/precheck`

明确不接：

- `GET /api/v1/ldap/templates/{systemCode}`

## 8. 实现范围

- 新增 `frontend/src/api/modules/ldap.ts`
- 新增 `frontend/src/types/ldap.ts`
- 新增 `frontend/src/views/ldap/LdapControlView.vue`
- 更新：
  - `frontend/src/router/index.ts`
  - `frontend/src/constants/navigation.ts`
  - `frontend/src/views/dashboard/DashboardView.vue`

## 9. 文档同步

实现完成后同步更新：

- `docs/project-design.md`

## 10. 结论

本方案以最小增量方式补齐 LDAP 控制面的核心使用场景，同时去掉当前价值较低的“权限树”导航入口，使前端信息架构更聚焦于当前实际可用能力。
