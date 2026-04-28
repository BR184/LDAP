export interface SyncBatchSummary {
  batch: SyncBatch
}

export interface SyncBatch {
  id: number
  batchNo: string
  batchType: string
  sourceType: string
  triggerMode: string
  status: string
  fileName?: string | null
  fileHash?: string | null
  summaryJson?: string | null
  operator?: string | null
  correlationBatchNo?: string | null
}

export interface SyncJob {
  id: number
  batchNo: string
  jobType: string
  targetType: string
  startTime?: string | null
  status: string
  requestJson?: string | null
  resultJson?: string | null
  errorMessage?: string | null
  operator?: string | null
  retryCount: number
}

export interface SyncDiff {
  id: number
  batchNo: string
  jobId: number
  targetType: string
  targetKey: string
  diffType: string
  sourceSnapshot?: string | null
  targetSnapshot?: string | null
  repairable: number
  status: string
}

export interface SyncBatchDetail {
  batch: SyncBatch
  jobs: SyncJob[]
  diffs: SyncDiff[]
}
