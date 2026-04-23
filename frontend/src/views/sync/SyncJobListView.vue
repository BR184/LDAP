<script setup lang="ts">
import { computed } from 'vue'
import { useMutation } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { executeSyncReconcile, previewSyncReconcile } from '@/api/modules/sync'
import type { SyncBatchDetail } from '@/types/sync'

const rows = [
  {
    id: 1001,
    batchNo: 'LDAP_RECONCILE_202604210001',
    jobType: 'LDAP_RECONCILE_USER',
    status: 'SUCCESS',
    operator: 'admin',
  },
  {
    id: 1002,
    batchNo: 'FEISHU_IMPORT_202604210002',
    jobType: 'FEISHU_USER_IMPORT',
    status: 'RUNNING',
    operator: 'system-scheduler',
  },
]

const previewMutation = useMutation({
  mutationFn: previewSyncReconcile,
})

const executeMutation = useMutation({
  mutationFn: () => executeSyncReconcile(true),
})

const actionLoading = computed(() => previewMutation.isPending.value || executeMutation.isPending.value)

function statusType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'RUNNING') return 'warning'
  if (status === 'FAIL') return 'danger'
  return 'info'
}

function buildSummary(detail: SyncBatchDetail) {
  return [
    `批次号：${detail.batch.batchNo}`,
    `批次类型：${detail.batch.batchType}`,
    `批次状态：${detail.batch.status}`,
    `任务数量：${detail.jobs.length}`,
    `差异数量：${detail.diffs.length}`,
  ].join('\n')
}

async function handlePreview() {
  const detail = await previewMutation.mutateAsync()
  await ElMessageBox.alert(buildSummary(detail), '对账预览结果', {
    type: 'info',
    confirmButtonText: '知道了',
  })
}

async function handleExecute() {
  try {
    await ElMessageBox.confirm(
      '确认执行一次 LDAP 对账吗？当前将按默认策略启用自动修复可修复差异。',
      '执行对账',
      {
        type: 'warning',
        confirmButtonText: '确认执行',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  const detail = await executeMutation.mutateAsync()
  ElMessage.success('对账执行完成')
  await ElMessageBox.alert(buildSummary(detail), '对账执行结果', {
    type: detail.batch.status === 'FAIL' ? 'error' : 'success',
    confirmButtonText: '知道了',
  })
}
</script>

<template>
  <PageContainer title="同步任务" description="同步中心页用于查看任务执行、批次详情和对账结果，是后续飞书导入与 LDAP 对账的统一入口。">
    <template #extra>
      <el-space>
        <el-button :loading="actionLoading" @click="handlePreview">对账预览</el-button>
        <el-button type="primary" :loading="actionLoading" @click="handleExecute">执行对账</el-button>
      </el-space>
    </template>

    <el-card class="idm-card" shadow="never">
      <el-table :data="rows">
        <el-table-column prop="batchNo" label="批次号" min-width="260" />
        <el-table-column prop="jobType" label="任务类型" min-width="220" />
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" min-width="160" />
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <el-space wrap>
              <el-button link type="primary" disabled>批次详情</el-button>
              <el-button link type="warning" disabled>任务重试</el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </PageContainer>
</template>
