import request from '@/api/request'
import type { SyncBatchDetail } from '@/types/sync'

export function previewSyncReconcile() {
  return request.post<never, SyncBatchDetail>('/v1/sync/reconcile/preview', {})
}

export function executeSyncReconcile(autoRepair = true) {
  return request.post<never, SyncBatchDetail>('/v1/sync/reconcile/execute', {
    autoRepair,
  })
}
