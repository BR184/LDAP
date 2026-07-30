export interface FeishuFullImportPayload {
  documentPath?: string
  remark?: string
}

export interface ConfirmImportPlanPayload {
  enabledItemIds: number[]
  confirmedItemIds: number[]
}

export interface ImportBatch {
  id: number
  batchCode: string
  fileName: string
  fileHash?: string | null
  sourceType: string
  totalItems: number
  enabledItems: number
  conflictItems: number
  status: string
  createdBy: string
  createdAt: string
  confirmedBy?: string | null
  confirmedAt?: string | null
  executedBy?: string | null
  executedAt?: string | null
  rollbackBy?: string | null
  rollbackAt?: string | null
  expiredAt?: string | null
  cancelledBy?: string | null
  cancelledAt?: string | null
  remark?: string | null
}

export interface ImportChangeItem {
  id: number
  batchId: number
  targetType: string
  targetKey: string
  changeType: string
  fieldKey?: string | null
  fieldLabel?: string | null
  beforeValue?: string | null
  afterValue?: string | null
  objectVersion?: number | null
  defaultEnabled: boolean
  enabled: boolean
  requiresConfirmation: boolean
  confirmed: boolean
  riskLevel: string
  blockReason?: string | null
  conflictCode?: string | null
  resolutionOptions?: string[]
  status: string
  errorMessage?: string | null
  retryCount: number
  executedAt?: string | null
  resolutionAction?: string | null
  resolvedBy?: string | null
  resolvedAt?: string | null
}

export interface ImportRollbackItem {
  id: number
  batchId: number
  changeItemId: number
  targetType: string
  targetKey: string
  rollbackAction: string
  restoreJson?: string | null
  status: string
  errorMessage?: string | null
  executedAt?: string | null
}

export interface ImportBatchDetail {
  batch: ImportBatch
  changeItems: ImportChangeItem[]
  rollbackItems: ImportRollbackItem[]
}

export interface ImportPlanReview {
  batch: ImportBatch
  statistics: ImportReviewStatistics
  userRows: UserReviewRow[]
  departmentRows: DepartmentReviewRow[]
  conflictRows: ConflictReviewRow[]
}

export interface ImportReviewStatistics {
  userRows: number
  departmentRows: number
  createRows: number
  updateRows: number
  resignRows: number
  conflictRows: number
  failedRows: number
  ldapFailedRows: number
}

export interface FieldChange {
  itemId: number
  fieldKey?: string | null
  fieldLabel?: string | null
  beforeValue?: string | null
  afterValue?: string | null
  riskLevel: string
  enabled: boolean
  requiresConfirmation: boolean
  confirmed: boolean
  status: string
  errorMessage?: string | null
}

export interface UserReviewRow {
  targetKey: string
  realName?: string | null
  employeeNo?: string | null
  departmentName?: string | null
  departmentPath?: string | null
  jobTitle?: string | null
  leaderRef?: string | null
  directLeaderRaw?: string | null
  employmentStatus?: string | null
  changeType: string
  changeSummary: string
  riskLevel: string
  enabled: boolean
  requiresConfirmation: boolean
  confirmed: boolean
  itemIds: number[]
  fieldChanges: FieldChange[]
}

export interface DepartmentReviewRow {
  targetKey: string
  deptName?: string | null
  parentDepartmentName?: string | null
  departmentPath?: string | null
  status?: string | null
  changeType: string
  changeSummary: string
  riskLevel: string
  enabled: boolean
  requiresConfirmation: boolean
  confirmed: boolean
  itemIds: number[]
  fieldChanges: FieldChange[]
}

export interface ConflictReviewRow {
  itemId: number
  targetType: string
  targetKey: string
  candidateRealName?: string | null
  employeeNo?: string | null
  existingUserId?: string | null
  existingRealName?: string | null
  blockReason?: string | null
  conflictCode?: string | null
  resolutionOptions?: string[]
  riskLevel: string
  status: string
  errorMessage?: string | null
  resolutionAction?: string | null
  resolvedBy?: string | null
  resolvedAt?: string | null
}
