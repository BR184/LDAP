# 同步中心实时任务页设计方案

## 1. 背景

当前前端“同步任务”页已经接入了：

- `POST /api/v1/sync/reconcile/preview`
- `POST /api/v1/sync/reconcile/execute`

但任务列表本身仍然是静态占位数据，`批次详情` 与 `任务重试` 也未接入真实后端能力。

后端已经具备以下接口：

- `GET /api/v1/sync/jobs`
- `GET /api/v1/sync/batches/{batchNo}`
- `POST /api/v1/sync/jobs/{id}/retry`

因此本次目标是在不重做整个同步中心的前提下，把任务列表、批次详情和任务重试接成真实数据。

## 2. 目标

- 任务列表改为实时展示 `GET /api/v1/sync/jobs`
- `批次详情` 可查看对应批次的 `batch / jobs / diffs`
- `任务重试` 可直接重试失败或需要重跑的任务
- 保持对账预览 / 执行按钮继续可用

## 3. 非目标

- 本次不新增独立的批次详情路由页
- 本次不补文件导入结果页
- 本次不做任务筛选、分页、导出

## 4. 推荐方案

采用“同步任务实时列表 + 详情弹窗 + 重试确认”的最小闭环方案：

- 列表区：直接显示后端实时任务列表
- 批次详情：使用弹窗承载
- 任务重试：点击后确认执行，成功后可直接查看新批次结果

这样可以避免引入新的详情页路由，同时保证现有同步中心马上具备真实运维价值。

## 5. 前端改动范围

- 扩展 `frontend/src/api/modules/sync.ts`
  - `fetchSyncJobs`
  - `fetchSyncBatchDetail`
  - `retrySyncJob`
- 扩展 `frontend/src/types/sync.ts`
  - 复用现有 `SyncBatchDetail`
- 改造 `frontend/src/views/sync/SyncJobListView.vue`
  - 列表接入真实任务数据
  - 新增批次详情弹窗
  - 新增任务重试逻辑

## 6. 交互设计

- 页面加载时拉取最近任务列表
- `批次详情`
  - 点击后按 `batchNo` 拉取批次详情
  - 展示批次概要、任务明细、差异明细
- `任务重试`
  - 点击后弹确认框
  - 调用重试接口
  - 成功后刷新列表，并弹出本次新批次的摘要结果
- `对账预览` / `执行对账`
  - 继续保留当前交互
  - 成功后刷新任务列表

## 7. 验证方式

- 执行：

```powershell
npm.cmd run check
```

## 8. 文档同步

- 更新 `docs/project-design.md`
  - 补充同步任务页当前已接入的真实接口能力
  - 补充批次详情与任务重试交互

## 9. 结论

本方案在不扩大范围的前提下，把同步中心从“静态演示页”提升为“可实时查看、可排查、可重试”的真实运维页面。
