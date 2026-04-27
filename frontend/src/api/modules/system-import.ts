import request from '@/api/request'
import type { SyncBatchDetail } from '@/types/sync'
import type { FeishuFullImportPayload } from '@/types/system-import'

export function executeFeishuFullImport(payload: FeishuFullImportPayload) {
  return request.post<never, SyncBatchDetail>('/v1/system/imports/feishu/full', payload)
}
