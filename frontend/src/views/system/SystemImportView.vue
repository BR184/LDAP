<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { CheckboxValueType, FormRules } from 'element-plus'
import { Check, CircleClose, RefreshRight, UploadFilled, VideoPlay, WarningFilled } from '@element-plus/icons-vue'
import {
  cancelImportPlan,
  confirmImportPlan,
  executeImportPlan,
  fetchImportPlanDetail,
  fetchImportPlanReview,
  fetchImportPlans,
  generateFeishuImportPlan,
  generateFeishuImportPlanByUpload,
  generateImportRollbackPlan,
  mergeImportConflictByEmployeeNumber,
  retryImportLdapFailures,
  rollbackImportPlan,
  skipImportConflict,
} from '@/api/modules/system-import'
import type {
  DepartmentReviewRow,
  FieldChange,
  ImportBatch,
  ImportBatchDetail,
  UserReviewRow,
} from '@/types/system-import'
import PersistentTableScrollFrame from '@/components/table-scroll/PersistentTableScrollFrame.vue'
import { useAuthStore } from '@/stores/auth'

type ReviewObjectType = 'USER' | 'DEPARTMENT' | 'CONFLICT'

interface ReviewRow {
  rowKey: string
  objectType: ReviewObjectType
  targetKey: string
  displayName: string
  employeeNo?: string | null
  departmentName?: string | null
  departmentPath?: string | null
  jobTitle?: string | null
  leaderText?: string | null
  status?: string | null
  changeType: string
  changeSummary: string
  riskLevel: string
  enabled: boolean
  requiresConfirmation: boolean
  confirmed: boolean
  itemIds: number[]
  fieldChanges: FieldChange[]
  blockReason?: string | null
  errorMessage?: string | null
  conflictCode?: string | null
  resolutionOptions: string[]
  existingUserId?: string | null
  existingRealName?: string | null
  resolutionAction?: string | null
  resolvedBy?: string | null
  resolvedAt?: string | null
}

const queryClient = useQueryClient()
const authStore = useAuthStore()

const form = reactive({
  documentPath: '',
  remark: 'manual-full-import',
})

const rules: FormRules<typeof form> = {}
const uploadedFile = ref<File | null>(null)
const selectedBatchId = ref<number | null>(null)
const selectedItemIds = ref<Set<number>>(new Set())
const confirmedItemIds = ref<Set<number>>(new Set())
const activeTab = ref('all')
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)

const plansQuery = useQuery({
  queryKey: ['import-plans'],
  queryFn: () => fetchImportPlans(50),
})

const detailQuery = useQuery({
  queryKey: computed(() => ['import-plan-detail', selectedBatchId.value]),
  queryFn: () => fetchImportPlanDetail(selectedBatchId.value as number),
  enabled: computed(() => selectedBatchId.value !== null),
})

const reviewQuery = useQuery({
  queryKey: computed(() => ['import-plan-review', selectedBatchId.value]),
  queryFn: () => fetchImportPlanReview(selectedBatchId.value as number),
  enabled: computed(() => selectedBatchId.value !== null),
})

const plans = computed(() => plansQuery.data.value || [])
const detail = computed(() => detailQuery.data.value || null)
const review = computed(() => reviewQuery.data.value || null)
const currentBatch = computed(() => review.value?.batch || detail.value?.batch || null)
const changeItems = computed(() => detail.value?.changeItems || [])
const rollbackItems = computed(() => detail.value?.rollbackItems || [])

const reviewRows = computed<ReviewRow[]>(() => {
  const data = review.value
  if (!data) {
    return []
  }
  const userRows = data.userRows.map((row) => userReviewRow(row))
  const departmentRows = data.departmentRows.map((row) => departmentReviewRow(row))
  const conflictRows = data.conflictRows.map((row) => ({
    rowKey: `CONFLICT:${row.itemId}`,
    objectType: 'CONFLICT' as const,
    targetKey: row.targetKey,
    displayName: row.candidateRealName || `${targetText(row.targetType)} / ${row.targetKey}`,
    employeeNo: row.employeeNo,
    changeType: 'CONFLICT',
    changeSummary: row.existingUserId
      ? `将更新 ${row.existingRealName || row.existingUserId} / ${row.existingUserId}`
      : row.blockReason || row.errorMessage || '阻断冲突',
    riskLevel: row.riskLevel,
    status: row.status,
    enabled: false,
    requiresConfirmation: true,
    confirmed: row.status === 'SKIPPED',
    itemIds: [row.itemId],
    fieldChanges: [],
    blockReason: row.blockReason,
    errorMessage: row.errorMessage,
    conflictCode: row.conflictCode,
    resolutionOptions: row.resolutionOptions || [],
    existingUserId: row.existingUserId,
    existingRealName: row.existingRealName,
    resolutionAction: row.resolutionAction,
    resolvedBy: row.resolvedBy,
    resolvedAt: row.resolvedAt,
  }))
  return [...userRows, ...departmentRows, ...conflictRows]
})

const filteredRows = computed(() => {
  const rawKeyword = keyword.value.trim().toLowerCase()
  return reviewRows.value.filter((row) => {
    if (activeTab.value !== 'all') {
      if (activeTab.value === 'user' && row.objectType !== 'USER') return false
      if (activeTab.value === 'department' && row.objectType !== 'DEPARTMENT') return false
      if (activeTab.value === 'conflict' && row.objectType !== 'CONFLICT') return false
      if (activeTab.value === 'create' && row.changeType !== 'CREATE') return false
      if (activeTab.value === 'update' && row.changeType !== 'UPDATE') return false
      if (activeTab.value === 'resign' && row.changeType !== 'RESIGN') return false
      if (activeTab.value === 'failed' && !row.fieldChanges.some((field) => field.status === 'FAILED' || field.status === 'LDAP_FAILED')) return false
    }
    if (!rawKeyword) {
      return true
    }
    return [
      row.targetKey,
      row.displayName,
      row.employeeNo,
      row.departmentName,
      row.departmentPath,
      row.jobTitle,
      row.leaderText,
      row.changeSummary,
    ].some((value) => String(value || '').toLowerCase().includes(rawKeyword))
  })
})

const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredRows.value.slice(start, start + pageSize.value)
})

const summary = computed(() => {
  const data = review.value?.statistics
  return {
    create: data?.createRows || 0,
    update: data?.updateRows || 0,
    resign: data?.resignRows || 0,
    conflict: data?.conflictRows || 0,
    failed: (data?.failedRows || 0) + (data?.ldapFailedRows || 0),
    totalObjects: (data?.userRows || 0) + (data?.departmentRows || 0) + (data?.conflictRows || 0),
  }
})

const hasBlocker = computed(() =>
  reviewRows.value.some((row) => row.riskLevel === 'BLOCKER' && row.status === 'PENDING'),
)
const selectedCount = computed(() => selectedItemIds.value.size)
const hasUnconfirmedEnabledRows = computed(() =>
  reviewRows.value.some((row) => isRowSelected(row) && row.requiresConfirmation && !isRowConfirmed(row)),
)
const canConfirm = computed(() =>
  authStore.can('IMPORT_PLAN_CONFIRM')
  && currentBatch.value?.status === 'DRAFT'
  && !hasBlocker.value
  && selectedCount.value > 0
  && !hasUnconfirmedEnabledRows.value,
)
const canExecute = computed(() => authStore.can('IMPORT_PLAN_EXECUTE') && currentBatch.value?.status === 'CONFIRMED')
const canCancel = computed(() => authStore.can('IMPORT_PLAN_CANCEL') && currentBatch.value?.status === 'DRAFT')
const canRollback = computed(() => authStore.can('IMPORT_PLAN_ROLLBACK') && currentBatch.value?.status === 'COMPLETED')
const canRetryLdap = computed(() =>
  authStore.can('IMPORT_PLAN_RETRY_LDAP')
  && currentBatch.value?.status === 'FAILED'
  && changeItems.value.some((item) => item.status === 'LDAP_FAILED'),
)

const generatePlanMutation = useMutation({
  mutationFn: generateFeishuImportPlan,
  onSuccess: async (nextDetail) => {
    ElMessage.success(`导入计划已生成：${nextDetail.batch.batchCode}`)
    await afterPlanChanged(nextDetail)
  },
})

const generateUploadPlanMutation = useMutation({
  mutationFn: ({ file, remark }: { file: File; remark?: string }) => generateFeishuImportPlanByUpload(file, remark),
  onSuccess: async (nextDetail) => {
    ElMessage.success(`导入计划已生成：${nextDetail.batch.batchCode}`)
    await afterPlanChanged(nextDetail)
  },
})

const confirmPlanMutation = useMutation({
  mutationFn: ({ batchId, itemIds, confirmedIds }: { batchId: number; itemIds: number[]; confirmedIds: number[] }) =>
    confirmImportPlan(batchId, {
      enabledItemIds: itemIds,
      confirmedItemIds: confirmedIds,
    }),
  onSuccess: async (nextDetail) => {
    ElMessage.success('导入计划已确认')
    await afterPlanChanged(nextDetail)
  },
})

const skipConflictMutation = useMutation({
  mutationFn: ({ batchId, itemId }: { batchId: number; itemId: number }) => skipImportConflict(batchId, itemId),
  onSuccess: async (nextDetail) => {
    ElMessage.success('冲突对象已从本批次排除')
    await afterPlanChanged(nextDetail)
  },
})

const mergeConflictMutation = useMutation({
  mutationFn: ({ batchId, itemId }: { batchId: number; itemId: number }) =>
    mergeImportConflictByEmployeeNumber(batchId, itemId),
  onSuccess: async (nextDetail) => {
    ElMessage.success('已按工号认定为同一员工，更新项已加入计划')
    await afterPlanChanged(nextDetail)
  },
})

const cancelPlanMutation = useMutation({
  mutationFn: cancelImportPlan,
  onSuccess: async (nextDetail) => {
    ElMessage.success('草稿计划已取消，可以重新上传文件')
    await afterPlanChanged(nextDetail)
  },
})

const executePlanMutation = useMutation({
  mutationFn: executeImportPlan,
  onSuccess: async (nextDetail) => {
    ElMessage.success(nextDetail.batch.status === 'COMPLETED' ? '导入计划已执行完成' : '导入计划执行完成，请检查失败项')
    await afterPlanChanged(nextDetail)
    await queryClient.invalidateQueries({ queryKey: ['department-tree'] })
    await queryClient.invalidateQueries({ queryKey: ['users'] })
  },
})

const rollbackPlanMutation = useMutation({
  mutationFn: rollbackImportPlan,
  onSuccess: async (nextDetail) => {
    ElMessage.success('导入批次已撤回')
    await afterPlanChanged(nextDetail)
    await queryClient.invalidateQueries({ queryKey: ['department-tree'] })
    await queryClient.invalidateQueries({ queryKey: ['users'] })
  },
})

const rollbackPreviewMutation = useMutation({
  mutationFn: generateImportRollbackPlan,
  onSuccess: async (nextDetail) => {
    ElMessage.success('撤回计划已生成')
    await afterPlanChanged(nextDetail)
  },
})

const retryLdapMutation = useMutation({
  mutationFn: retryImportLdapFailures,
  onSuccess: async (nextDetail) => {
    ElMessage.success('LDAP 失败项已重试')
    await afterPlanChanged(nextDetail)
  },
})

const actionLoading = computed(() =>
  generatePlanMutation.isPending.value
  || generateUploadPlanMutation.isPending.value
  || confirmPlanMutation.isPending.value
  || skipConflictMutation.isPending.value
  || mergeConflictMutation.isPending.value
  || cancelPlanMutation.isPending.value
  || executePlanMutation.isPending.value
  || rollbackPlanMutation.isPending.value
  || rollbackPreviewMutation.isPending.value
  || retryLdapMutation.isPending.value,
)

watch(plans, (nextPlans) => {
  if (selectedBatchId.value === null && nextPlans.length > 0) {
    selectedBatchId.value = nextPlans[0].id
  }
})

watch(detail, (nextDetail) => {
  if (!nextDetail) {
    selectedItemIds.value = new Set()
    confirmedItemIds.value = new Set()
    return
  }
  selectedItemIds.value = new Set(nextDetail.changeItems
    .filter((item) => item.enabled && item.status === 'PENDING')
    .map((item) => item.id))
  confirmedItemIds.value = new Set(nextDetail.changeItems.filter((item) => item.confirmed).map((item) => item.id))
})

watch([activeTab, keyword, pageSize], () => {
  currentPage.value = 1
})

async function handleSubmit() {
  if (uploadedFile.value) {
    await generateUploadPlanMutation.mutateAsync({
      file: uploadedFile.value,
      remark: form.remark.trim() || undefined,
    })
    return
  }
  if (!form.documentPath.trim()) {
    ElMessage.warning('请上传文件，或填写后端受控目录中的导入文件路径')
    return
  }
  await generatePlanMutation.mutateAsync({
    documentPath: form.documentPath.trim(),
    remark: form.remark.trim() || undefined,
  })
}

function handleUploadChange(uploadFile: { raw?: File }) {
  uploadedFile.value = uploadFile.raw || null
  if (uploadedFile.value) {
    form.documentPath = uploadedFile.value.name
  }
}

function handleUploadRemove() {
  uploadedFile.value = null
}

function selectBatch(batch: ImportBatch) {
  selectedBatchId.value = batch.id
}

function toggleRowEnabled(row: ReviewRow, value: CheckboxValueType) {
  const next = new Set(selectedItemIds.value)
  for (const itemId of row.itemIds) {
    if (Boolean(value)) {
      next.add(itemId)
    } else {
      next.delete(itemId)
    }
  }
  selectedItemIds.value = next
}

function toggleRowConfirmed(row: ReviewRow, value: CheckboxValueType) {
  const next = new Set(confirmedItemIds.value)
  const targetIds = row.fieldChanges.length > 0
    ? row.fieldChanges.filter((field) => field.requiresConfirmation).map((field) => field.itemId)
    : row.itemIds
  for (const itemId of targetIds) {
    if (Boolean(value)) {
      next.add(itemId)
    } else {
      next.delete(itemId)
    }
  }
  confirmedItemIds.value = next
}

async function handleConfirmPlan() {
  if (!currentBatch.value) {
    return
  }
  await ElMessageBox.confirm(
    `确认当前计划中的 ${selectedCount.value} 个变更项吗？确认后才能执行写入。`,
    '确认导入计划',
    {
      type: 'warning',
      confirmButtonText: '确认计划',
      cancelButtonText: '取消',
    },
  )
  await confirmPlanMutation.mutateAsync({
    batchId: currentBatch.value.id,
    itemIds: Array.from(selectedItemIds.value),
    confirmedIds: Array.from(confirmedItemIds.value),
  })
}

async function handleExecutePlan() {
  if (!currentBatch.value) {
    return
  }
  await ElMessageBox.confirm(
    `执行后会写入 ${currentBatch.value.enabledItems} 个变更到 MySQL 和 LDAP。确认执行吗？`,
    '执行导入计划',
    {
      type: 'warning',
      confirmButtonText: '执行',
      cancelButtonText: '取消',
    },
  )
  await executePlanMutation.mutateAsync(currentBatch.value.id)
}

async function handleSkipConflict(row: ReviewRow) {
  if (!currentBatch.value || row.objectType !== 'CONFLICT' || row.itemIds.length !== 1) {
    return
  }
  await ElMessageBox.confirm(
    '排除后，该冲突及其关联的新增、修改或离职变更都不会在本批次执行，其他正常变更仍可继续。确认排除吗？',
    '排除冲突对象',
    {
      type: 'warning',
      confirmButtonText: '确认排除',
      cancelButtonText: '取消',
    },
  )
  await skipConflictMutation.mutateAsync({
    batchId: currentBatch.value.id,
    itemId: row.itemIds[0] as number,
  })
}

async function handleMergeConflict(row: ReviewRow) {
  if (!currentBatch.value || row.objectType !== 'CONFLICT' || row.itemIds.length !== 1) {
    return
  }
  const existingAccount = row.existingUserId
    ? `${row.existingRealName || row.existingUserId}（${row.existingUserId}）`
    : '工号对应的现有账号'
  await ElMessageBox.confirm(
    `确认将“${row.displayName}”认定为同一员工并更新 ${existingAccount} 吗？现有登录账号、LDAP UID、密码、内网邮箱和角色不会改变。`,
    '按工号确认更新',
    {
      type: 'warning',
      confirmButtonText: '确认更新',
      cancelButtonText: '返回',
    },
  )
  await mergeConflictMutation.mutateAsync({
    batchId: currentBatch.value.id,
    itemId: row.itemIds[0] as number,
  })
}

async function handleCancelPlan() {
  if (!currentBatch.value) {
    return
  }
  await ElMessageBox.confirm(
    '取消后不会执行当前计划，计划与冲突记录仍会保留。你可以修正源文件后立即重新生成计划。',
    '取消草稿计划',
    {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
    },
  )
  await cancelPlanMutation.mutateAsync(currentBatch.value.id)
}

async function handleRollbackPreview() {
  if (!currentBatch.value) {
    return
  }
  await rollbackPreviewMutation.mutateAsync(currentBatch.value.id)
}

async function handleRollbackPlan() {
  if (!currentBatch.value) {
    return
  }
  await ElMessageBox.confirm(
    '撤回会按执行前快照恢复数据。确认撤回吗？',
    '撤回导入批次',
    {
      type: 'warning',
      confirmButtonText: '撤回',
      cancelButtonText: '取消',
    },
  )
  await rollbackPlanMutation.mutateAsync(currentBatch.value.id)
}

async function handleRetryLdapFailures() {
  if (!currentBatch.value) {
    return
  }
  await retryLdapMutation.mutateAsync(currentBatch.value.id)
}

async function refreshAll() {
  await queryClient.invalidateQueries({ queryKey: ['import-plans'] })
  if (selectedBatchId.value !== null) {
    await queryClient.invalidateQueries({ queryKey: ['import-plan-detail', selectedBatchId.value] })
    await queryClient.invalidateQueries({ queryKey: ['import-plan-review', selectedBatchId.value] })
  }
}

async function afterPlanChanged(nextDetail: ImportBatchDetail) {
  selectedBatchId.value = nextDetail.batch.id
  queryClient.setQueryData(['import-plan-detail', nextDetail.batch.id], nextDetail)
  await queryClient.invalidateQueries({ queryKey: ['import-plans'] })
  await queryClient.invalidateQueries({ queryKey: ['import-plan-review', nextDetail.batch.id] })
}

function userReviewRow(row: UserReviewRow): ReviewRow {
  return {
    rowKey: `USER:${row.targetKey}`,
    objectType: 'USER',
    targetKey: row.targetKey,
    displayName: row.realName || row.targetKey,
    employeeNo: row.employeeNo,
    departmentName: row.departmentName,
    departmentPath: row.departmentPath,
    jobTitle: row.jobTitle,
    leaderText: row.directLeaderRaw || row.leaderRef,
    status: row.employmentStatus,
    changeType: row.changeType,
    changeSummary: row.changeSummary,
    riskLevel: row.riskLevel,
    enabled: row.enabled,
    requiresConfirmation: row.requiresConfirmation,
    confirmed: row.confirmed,
    itemIds: row.itemIds,
    fieldChanges: row.fieldChanges,
    resolutionOptions: [],
  }
}

function departmentReviewRow(row: DepartmentReviewRow): ReviewRow {
  return {
    rowKey: `DEPARTMENT:${row.targetKey}`,
    objectType: 'DEPARTMENT',
    targetKey: row.targetKey,
    displayName: row.deptName || row.targetKey,
    departmentName: row.deptName,
    departmentPath: row.departmentPath,
    leaderText: row.parentDepartmentName,
    status: row.status,
    changeType: row.changeType,
    changeSummary: row.changeSummary,
    riskLevel: row.riskLevel,
    enabled: row.enabled,
    requiresConfirmation: row.requiresConfirmation,
    confirmed: row.confirmed,
    itemIds: row.itemIds,
    fieldChanges: row.fieldChanges,
    resolutionOptions: [],
  }
}

function isRowSelected(row: ReviewRow) {
  return row.itemIds.length > 0 && row.itemIds.every((id) => selectedItemIds.value.has(id))
}

function isRowConfirmed(row: ReviewRow) {
  if (!row.requiresConfirmation) {
    return true
  }
  const confirmationIds = row.fieldChanges.length > 0
    ? row.fieldChanges.filter((field) => field.requiresConfirmation).map((field) => field.itemId)
    : row.itemIds
  return confirmationIds.length > 0 && confirmationIds.every((id) => confirmedItemIds.value.has(id))
}

function statusTagType(status: string) {
  if (status === 'COMPLETED' || status === 'EXECUTED') return 'success'
  if (status === 'FAILED' || status === 'LDAP_FAILED' || status === 'BLOCKER') return 'danger'
  if (status === 'CONFIRMED' || status === 'EXECUTING') return 'warning'
  if (status === 'ROLLED_BACK' || status === 'SKIPPED' || status === 'EXPIRED' || status === 'CANCELLED') return 'info'
  return 'primary'
}

function riskTagType(risk: string) {
  if (risk === 'BLOCKER') return 'danger'
  if (risk === 'HIGH') return 'warning'
  if (risk === 'MEDIUM') return 'primary'
  return 'info'
}

function changeTagType(changeType: string) {
  if (changeType === 'CREATE') return 'success'
  if (changeType === 'RESIGN' || changeType === 'DISABLE') return 'warning'
  if (changeType === 'CONFLICT') return 'danger'
  return 'primary'
}

function targetText(type: string) {
  return type === 'USER' ? '用户' : type === 'DEPARTMENT' ? '部门' : type
}

function objectCaption(row: ReviewRow) {
  return row.objectType === 'USER' ? `用户 / ${row.targetKey}` : targetText(row.objectType)
}
</script>

<template>
  <PageContainer
    title="文件导入"
    description="生成飞书花名册导入计划，按对象审核后执行，并支持撤回和 LDAP 失败重试。"
  >
    <div class="system-import-layout">
      <div class="top-grid">
        <el-card v-if="authStore.can('IMPORT_PLAN_CREATE')" class="idm-card import-generate" shadow="never">
          <template #header>
            <div class="section-header">
              <strong>生成计划</strong>
              <span class="idm-muted">上传 XLSX / JSON 文件，或填写后端受控目录中的文件路径。</span>
            </div>
          </template>

          <el-form :model="form" :rules="rules" label-position="top">
            <el-form-item label="导入文件">
              <el-upload
                :auto-upload="false"
                :limit="1"
                drag
                :on-change="handleUploadChange"
                :on-remove="handleUploadRemove"
                :show-file-list="true"
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">
                  将文件拖到此处，或 <em>点击选择文件</em>
                </div>
                <template #tip>
                  <div class="el-upload__tip">支持 `.xlsx` 和 `.json`。</div>
                </template>
              </el-upload>
            </el-form-item>

            <el-form-item label="导入文件路径">
              <el-input v-model="form.documentPath" placeholder="例如 docs/feishu-import/full-demo.json" />
            </el-form-item>

            <el-form-item label="批次备注">
              <el-input v-model="form.remark" placeholder="可选，例如 2026-06 花名册导入" />
            </el-form-item>

            <div class="form-actions">
              <el-button :icon="RefreshRight" @click="refreshAll">刷新</el-button>
              <el-button type="primary" :icon="UploadFilled" :loading="actionLoading" @click="handleSubmit">
                生成导入计划
              </el-button>
            </div>
          </el-form>
        </el-card>

        <el-card class="idm-card import-plan-list" shadow="never">
          <template #header>
            <div class="section-header">
              <strong>计划列表</strong>
              <span class="idm-muted">同一时间只允许一个未完成计划。</span>
            </div>
          </template>

          <PersistentTableScrollFrame>
            <el-table
              v-loading="plansQuery.isLoading.value || plansQuery.isFetching.value"
              :data="plans"
              border
              height="356"
              highlight-current-row
              @row-click="selectBatch"
            >
            <el-table-column prop="batchCode" label="计划编号" min-width="210" show-overflow-tooltip />
            <el-table-column prop="fileName" label="文件" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="totalItems" label="变更" width="80" align="right" />
            </el-table>
          </PersistentTableScrollFrame>
        </el-card>
      </div>

      <el-card class="idm-card import-plan-detail" shadow="never">
        <template #header>
          <div class="detail-header">
            <div class="section-header">
              <strong>计划详情</strong>
              <span v-if="currentBatch" class="idm-muted">{{ currentBatch.batchCode }} / {{ currentBatch.fileName }}</span>
              <span v-else class="idm-muted">选择或生成一个计划后查看详情。</span>
            </div>
            <div v-if="currentBatch" class="detail-actions">
              <el-button v-if="authStore.can('IMPORT_PLAN_CONFIRM')" :icon="Check" :disabled="!canConfirm" :loading="confirmPlanMutation.isPending.value" @click="handleConfirmPlan">
                确认计划
              </el-button>
              <el-button v-if="authStore.can('IMPORT_PLAN_EXECUTE')" type="primary" :icon="VideoPlay" :disabled="!canExecute" :loading="executePlanMutation.isPending.value" @click="handleExecutePlan">
                执行计划
              </el-button>
              <el-button v-if="canCancel" type="danger" plain :icon="CircleClose" :loading="cancelPlanMutation.isPending.value" @click="handleCancelPlan">
                取消计划
              </el-button>
              <el-button v-if="canRetryLdap" type="warning" :loading="retryLdapMutation.isPending.value" @click="handleRetryLdapFailures">
                重试 LDAP
              </el-button>
              <el-button v-if="authStore.can('IMPORT_PLAN_ROLLBACK')" :disabled="!canRollback" :loading="rollbackPreviewMutation.isPending.value" @click="handleRollbackPreview">
                生成撤回计划
              </el-button>
              <el-button v-if="authStore.can('IMPORT_PLAN_ROLLBACK')" type="danger" plain :disabled="!canRollback && rollbackItems.length === 0" :loading="rollbackPlanMutation.isPending.value" @click="handleRollbackPlan">
                撤回
              </el-button>
            </div>
          </div>
        </template>

        <el-empty v-if="!currentBatch" description="暂无导入计划" />

        <template v-else>
          <div class="summary-grid">
            <div class="summary-item"><span>状态</span><el-tag :type="statusTagType(currentBatch.status)">{{ currentBatch.status }}</el-tag></div>
            <div class="summary-item"><span>对象</span><strong>{{ summary.totalObjects }}</strong></div>
            <div class="summary-item"><span>已选变更</span><strong>{{ selectedCount }}</strong></div>
            <div class="summary-item"><span>新增</span><strong>{{ summary.create }}</strong></div>
            <div class="summary-item"><span>修改</span><strong>{{ summary.update }}</strong></div>
            <div class="summary-item"><span>离职</span><strong>{{ summary.resign }}</strong></div>
            <div class="summary-item"><span>冲突</span><strong>{{ summary.conflict }}</strong></div>
            <div class="summary-item"><span>失败</span><strong>{{ summary.failed }}</strong></div>
          </div>

          <el-alert
            v-if="currentBatch.status === 'DRAFT' && hasUnconfirmedEnabledRows"
            :closable="false"
            show-icon
            type="warning"
            title="存在已勾选但未确认的高风险对象，请确认后再提交计划。"
          />
          <el-alert
            v-if="hasBlocker"
            :closable="false"
            show-icon
            type="error"
            title="存在未处理的阻断冲突。请在冲突行选择确认更新或排除；无法安全确认的冲突需要修正源文件。"
          />

          <div class="review-toolbar">
            <el-tabs v-model="activeTab" class="review-tabs">
              <el-tab-pane label="全部" name="all" />
              <el-tab-pane label="用户" name="user" />
              <el-tab-pane label="部门" name="department" />
              <el-tab-pane label="新增" name="create" />
              <el-tab-pane label="修改" name="update" />
              <el-tab-pane label="离职" name="resign" />
              <el-tab-pane label="冲突" name="conflict" />
              <el-tab-pane label="失败" name="failed" />
            </el-tabs>
            <el-input v-model="keyword" class="review-search" clearable placeholder="搜索姓名、工号、部门、岗位、上级" />
          </div>

          <PersistentTableScrollFrame>
            <el-table
              v-loading="reviewQuery.isLoading.value || reviewQuery.isFetching.value"
              :data="pagedRows"
              border
              height="600"
              row-key="rowKey"
              class="review-table"
            >
            <el-table-column type="expand">
              <template #default="{ row }">
                <div v-if="row.fieldChanges.length > 0" class="field-change-panel">
                  <PersistentTableScrollFrame>
                    <el-table :data="row.fieldChanges" border size="small">
                    <el-table-column prop="fieldLabel" label="字段" min-width="140">
                      <template #default="{ row: field }">{{ field.fieldLabel || row.changeType }}</template>
                    </el-table-column>
                    <el-table-column prop="beforeValue" label="原值" min-width="180">
                      <template #default="{ row: field }"><span class="multiline-value">{{ field.beforeValue || '--' }}</span></template>
                    </el-table-column>
                    <el-table-column prop="afterValue" label="新值" min-width="180">
                      <template #default="{ row: field }"><span class="multiline-value">{{ field.afterValue || '--' }}</span></template>
                    </el-table-column>
                    <el-table-column label="风险" width="100" align="center">
                      <template #default="{ row: field }"><el-tag :type="riskTagType(field.riskLevel)">{{ field.riskLevel }}</el-tag></template>
                    </el-table-column>
                    <el-table-column label="状态" width="120" align="center">
                      <template #default="{ row: field }"><el-tag :type="statusTagType(field.status)">{{ field.status }}</el-tag></template>
                    </el-table-column>
                    </el-table>
                  </PersistentTableScrollFrame>
                </div>
                <div v-else class="field-change-panel">
                  <el-alert :closable="false" type="error" :title="row.blockReason || row.errorMessage || '暂无字段明细'" />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="执行" width="82" align="center">
              <template #default="{ row }">
                <el-checkbox
                  :model-value="isRowSelected(row)"
                  :disabled="!authStore.can('IMPORT_PLAN_CONFIRM') || currentBatch?.status !== 'DRAFT' || row.riskLevel === 'BLOCKER'"
                  @change="(value: CheckboxValueType) => toggleRowEnabled(row, value)"
                />
              </template>
            </el-table-column>
            <el-table-column label="对象" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="object-cell">
                  <strong>{{ row.displayName }}</strong>
                  <span>{{ objectCaption(row) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="employeeNo" label="工号" width="120" show-overflow-tooltip />
            <el-table-column label="部门" min-width="240">
              <template #default="{ row }">{{ row.departmentPath || row.departmentName || '--' }}</template>
            </el-table-column>
            <el-table-column prop="jobTitle" label="岗位" width="150" show-overflow-tooltip />
            <el-table-column prop="leaderText" label="直属上级" width="150" show-overflow-tooltip />
            <el-table-column label="变更" width="110" align="center">
              <template #default="{ row }"><el-tag :type="changeTagType(row.changeType)">{{ row.changeType }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="changeSummary" label="摘要" min-width="180" show-overflow-tooltip />
            <el-table-column label="风险" width="100" align="center">
              <template #default="{ row }"><el-tag :type="riskTagType(row.riskLevel)">{{ row.riskLevel }}</el-tag></template>
            </el-table-column>
            <el-table-column label="确认" width="90" align="center">
              <template #default="{ row }">
                <el-checkbox
                  :model-value="isRowConfirmed(row)"
                  :disabled="!authStore.can('IMPORT_PLAN_CONFIRM') || currentBatch?.status !== 'DRAFT' || !row.requiresConfirmation || !isRowSelected(row)"
                  @change="(value: CheckboxValueType) => toggleRowConfirmed(row, value)"
                />
              </template>
            </el-table-column>
            <el-table-column label="处理" width="196" align="center">
              <template #default="{ row }">
                <div
                  v-if="row.objectType === 'CONFLICT' && row.status === 'PENDING' && currentBatch?.status === 'DRAFT'"
                  class="conflict-actions"
                >
                  <el-button
                    v-if="authStore.can('IMPORT_PLAN_CONFLICT_MERGE') && row.resolutionOptions.includes('MERGE_BY_EMPLOYEE_NO')"
                    type="primary"
                    text
                    :icon="Check"
                    :loading="mergeConflictMutation.isPending.value"
                    @click="handleMergeConflict(row)"
                  >
                    确认更新
                  </el-button>
                  <el-button
                    v-if="authStore.can('IMPORT_PLAN_CONFLICT_RESOLVE') && row.resolutionOptions.includes('SKIP_RELATED_CHANGES')"
                    type="warning"
                    text
                    :loading="skipConflictMutation.isPending.value"
                    @click="handleSkipConflict(row)"
                  >
                    排除
                  </el-button>
                </div>
                <el-tag
                  v-else-if="row.objectType === 'CONFLICT' && row.status === 'SKIPPED'"
                  :type="row.resolutionAction === 'MERGE_BY_EMPLOYEE_NO' ? 'success' : 'info'"
                >
                  {{ row.resolutionAction === 'MERGE_BY_EMPLOYEE_NO' ? '已转为更新' : '已排除' }}
                </el-tag>
                <span v-else>--</span>
              </template>
            </el-table-column>
            <el-table-column label="说明" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.blockReason"><el-icon><WarningFilled /></el-icon> {{ row.blockReason }}</span>
                <span v-else-if="row.errorMessage">{{ row.errorMessage }}</span>
                <span v-else>--</span>
              </template>
            </el-table-column>
            </el-table>
          </PersistentTableScrollFrame>

          <div class="pagination-row">
            <span class="idm-muted">共 {{ filteredRows.length }} 个对象</span>
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50, 100]"
              layout="sizes, prev, pager, next"
              :total="filteredRows.length"
            />
          </div>
        </template>
      </el-card>
    </div>
  </PageContainer>
</template>

<style scoped lang="scss">
.system-import-layout {
  display: grid;
  gap: 16px;
}

.top-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.8fr) minmax(0, 1.2fr);
  gap: 16px;
}

.section-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-actions,
.detail-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}

.review-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
}

.review-tabs {
  min-width: 0;
  flex: 1;
}

.review-search {
  width: 320px;
}

.review-table {
  margin-top: 8px;
}

.field-change-panel {
  padding: 12px;
  background: var(--el-fill-color-light);
}

.multiline-value {
  white-space: pre-line;
  word-break: break-word;
}

.object-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;

  span {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}

.conflict-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-width: 0;

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}

.pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

@media (max-width: 1200px) {
  .top-grid {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .review-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .review-search {
    width: 100%;
  }
}

@media (max-width: 720px) {
  .detail-header {
    flex-direction: column;
  }

  .detail-actions,
  .form-actions {
    justify-content: flex-start;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
