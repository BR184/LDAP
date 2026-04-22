export interface SyncBatchSummary {
  batch: {
    batchNo: string
    batchType: string
    status: string
    triggerMode: string
    sourceType: string
    fileName?: string | null
  }
}
