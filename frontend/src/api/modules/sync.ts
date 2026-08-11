import request from '@/api/request'
import type { SyncBatchDetail, SyncJob } from '@/types/sync'

export function previewSyncReconcile() {
  return request.post<never, SyncBatchDetail>('/v2/sync/reconcile/preview', {})
}

export function executeSyncReconcile(autoRepair = true) {
  return request.post<never, SyncBatchDetail>('/v2/sync/reconcile/execute', {
    autoRepair,
  })
}

export function fetchSyncJobs() {
  return request.get<never, SyncJob[]>('/v2/sync/jobs')
}

export function fetchSyncBatchDetail(batchNo: string) {
  return request.get<never, SyncBatchDetail>(`/v2/sync/batches/${batchNo}`)
}

export function retrySyncJob(jobId: number) {
  return request.post<never, SyncBatchDetail>(`/v2/sync/jobs/${jobId}/retry`, {})
}
