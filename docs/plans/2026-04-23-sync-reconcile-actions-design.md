# 同步任务页对账预览与执行按钮接入设计

## 1. 背景

当前前端“同步任务”页仍保留静态示例任务列表，页面顶部的“对账预览”和“执行对账”按钮尚未接入真实后端接口。

后端已经提供以下接口：

- `POST /api/v1/sync/reconcile/preview`
- `POST /api/v1/sync/reconcile/execute`

本次只补齐这两个按钮的真实调用逻辑，不扩大到同步任务列表实时化、批次详情页和任务重试页。

## 2. 目标

- 将“对账预览”按钮接入真实后端接口
- 将“执行对账”按钮接入真实后端接口
- 在页面内给出批次结果的最小可用反馈
- 保持当前静态任务列表不变

## 3. 非目标

- 不接入 `GET /api/v1/sync/jobs`
- 不接入 `GET /api/v1/sync/batches/{batchNo}`
- 不实现“批次详情”页面
- 不实现“任务重试”逻辑
- 不重构同步中心整体信息架构

## 4. 推荐方案

采用“按钮直连后端 + 弹窗摘要反馈”的最小方案：

- `对账预览`
  - 调用 `POST /api/v1/sync/reconcile/preview`
  - 成功后弹出摘要结果
- `执行对账`
  - 先二次确认
  - 调用 `POST /api/v1/sync/reconcile/execute`
  - 默认 `autoRepair = true`
  - 成功后弹出摘要结果

结果摘要统一展示：

- 批次号
- 批次类型
- 批次状态
- 任务数量
- 差异数量

## 5. 前端改动范围

- 新增 `frontend/src/api/modules/sync.ts`
  - `previewSyncReconcile`
  - `executeSyncReconcile`
- 扩展 `frontend/src/types/sync.ts`
  - `SyncBatch`
  - `SyncJob`
  - `SyncDiff`
  - `SyncBatchDetail`
- 改造 `frontend/src/views/sync/SyncJobListView.vue`

## 6. 交互设计

- “对账预览”
  - 点击即执行
  - 使用普通信息弹窗显示摘要
- “执行对账”
  - 先确认，再执行
  - 成功后提示“对账执行完成”
  - 再用弹窗展示摘要
- 页面顶部两个按钮共享 loading 状态，避免重复提交

## 7. 设计取舍

为什么不直接把任务列表也接成真实数据：

- 当前需求明确只补“对账预览 / 执行对账”两个预留接口
- 同步任务列表、批次详情、任务重试都属于更大的同步中心收口范围
- 先让按钮真实可用，能最快形成闭环

## 8. 验证方式

- 执行前端类型检查：

```powershell
npm.cmd run check
```

## 9. 结论

本方案以最小增量方式打通了同步任务页的两项核心操作，使管理员能够从前端直接触发 LDAP 对账预览与执行，同时避免把后续批次详情与任务重试能力过早卷入当前范围。
