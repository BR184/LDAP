<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { executeSyncReconcile, fetchSyncBatchDetail, fetchSyncJobs, previewSyncReconcile, retrySyncJob } from '@/api/modules/sync'
import type { SyncBatchDetail, SyncJob } from '@/types/sync'
import PersistentTableScrollFrame from '@/components/table-scroll/PersistentTableScrollFrame.vue'
import { useAuthStore } from '@/stores/auth'

const queryClient = useQueryClient()
const authStore = useAuthStore()

const detailVisible = ref(false)
const detailLoading = ref(false)
const currentBatchDetail = ref<SyncBatchDetail | null>(null)
const pagination = ref({
  page: 1,
  pageSize: 10,
})

const jobsQuery = useQuery({
  queryKey: ['sync', 'jobs'],
  queryFn: fetchSyncJobs,
})

const previewMutation = useMutation({
  mutationFn: previewSyncReconcile,
  onSuccess: async () => {
    await refreshJobs()
  },
})

const executeMutation = useMutation({
  mutationFn: () => executeSyncReconcile(true),
  onSuccess: async () => {
    await refreshJobs()
  },
})

const retryMutation = useMutation({
  mutationFn: (jobId: number) => retrySyncJob(jobId),
  onSuccess: async () => {
    await refreshJobs()
  },
})

const rows = computed(() => jobsQuery.data.value || [])
const pagedRows = computed(() => {
  const start = (pagination.value.page - 1) * pagination.value.pageSize
  return rows.value.slice(start, start + pagination.value.pageSize)
})
const actionLoading = computed(
  () => previewMutation.isPending.value || executeMutation.isPending.value || retryMutation.isPending.value,
)

function statusType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'RUNNING') return 'warning'
  if (status === 'FAIL') return 'danger'
  if (status === 'PARTIAL_SUCCESS') return 'warning'
  return 'info'
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '--'
  }
  return value.replace('T', ' ')
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

async function refreshJobs() {
  pagination.value.page = 1
  await queryClient.invalidateQueries({ queryKey: ['sync', 'jobs'] })
}

async function openBatchDetail(batchNo: string) {
  detailVisible.value = true
  detailLoading.value = true
  currentBatchDetail.value = null

  try {
    currentBatchDetail.value = await fetchSyncBatchDetail(batchNo)
  } finally {
    detailLoading.value = false
  }
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

async function handleRetry(job: SyncJob) {
  try {
    await ElMessageBox.confirm(
      `确认重试任务 ${job.jobType}（批次 ${job.batchNo}）吗？`,
      '任务重试',
      {
        type: 'warning',
        confirmButtonText: '确认重试',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  const detail = await retryMutation.mutateAsync(job.id)
  ElMessage.success('任务重试已触发')
  await ElMessageBox.alert(buildSummary(detail), '任务重试结果', {
    type: detail.batch.status === 'FAIL' ? 'error' : 'success',
    confirmButtonText: '知道了',
  })
}
</script>

<template>
  <PageContainer title="同步任务" description="同步中心页用于查看任务执行、批次详情和对账结果，是后续飞书导入与 LDAP 对账的统一入口。">
    <template #extra>
      <el-space>
        <el-button v-if="authStore.can('SYNC_RECONCILE_PREVIEW')" :loading="actionLoading" @click="handlePreview">对账预览</el-button>
        <el-button v-if="authStore.can('SYNC_RECONCILE_EXECUTE')" type="primary" :loading="actionLoading" @click="handleExecute">执行对账</el-button>
      </el-space>
    </template>

    <el-card class="idm-card" shadow="never">
      <PersistentTableScrollFrame>
        <el-table v-loading="jobsQuery.isLoading.value || jobsQuery.isFetching.value" :data="pagedRows" border>
        <el-table-column prop="batchNo" label="批次号" min-width="260" />
        <el-table-column prop="jobType" label="任务类型" min-width="220" />
        <el-table-column label="任务开始时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="140">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" min-width="160" />
        <el-table-column label="错误原因" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.errorMessage || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <el-space wrap>
              <el-button v-if="authStore.can('SYNC_BATCH_DETAIL')" link type="primary" @click="openBatchDetail(row.batchNo)">批次详情</el-button>
              <el-button v-if="authStore.can('SYNC_JOB_RETRY')" link type="warning" :disabled="actionLoading" @click="handleRetry(row)">任务重试</el-button>
            </el-space>
          </template>
        </el-table-column>
        </el-table>
      </PersistentTableScrollFrame>

      <div class="table-footer">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :background="true"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="rows.length"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="批次详情" width="960px">
      <el-skeleton v-if="detailLoading" animated :rows="8" />

      <template v-else-if="currentBatchDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="批次号">{{ currentBatchDetail.batch.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="批次状态">
            <el-tag :type="statusType(currentBatchDetail.batch.status)">{{ currentBatchDetail.batch.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="批次类型">{{ currentBatchDetail.batch.batchType }}</el-descriptions-item>
          <el-descriptions-item label="触发方式">{{ currentBatchDetail.batch.triggerMode }}</el-descriptions-item>
          <el-descriptions-item label="来源类型">{{ currentBatchDetail.batch.sourceType }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ currentBatchDetail.batch.operator || '--' }}</el-descriptions-item>
          <el-descriptions-item label="文件名" :span="2">{{ currentBatchDetail.batch.fileName || '--' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider>任务列表</el-divider>

        <PersistentTableScrollFrame>
          <el-table :data="currentBatchDetail.jobs" border>
          <el-table-column prop="jobType" label="任务类型" min-width="220" />
          <el-table-column prop="targetType" label="目标类型" min-width="140" />
          <el-table-column label="开始时间" min-width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.startTime) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="120">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="operator" label="操作人" min-width="140" />
          <el-table-column prop="errorMessage" label="错误原因" min-width="260" show-overflow-tooltip />
          </el-table>
        </PersistentTableScrollFrame>

        <el-divider>差异列表</el-divider>

        <PersistentTableScrollFrame>
          <el-table :data="currentBatchDetail.diffs" border>
          <el-table-column prop="targetType" label="目标类型" min-width="140" />
          <el-table-column prop="targetKey" label="目标标识" min-width="220" />
          <el-table-column prop="diffType" label="差异类型" min-width="180" />
          <el-table-column label="修复状态" min-width="140">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          </el-table>
        </PersistentTableScrollFrame>
      </template>

      <el-empty v-else description="暂无批次详情" />
    </el-dialog>
  </PageContainer>
</template>

<style scoped lang="scss">
.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
