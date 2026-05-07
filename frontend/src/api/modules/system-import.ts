import request from '@/api/request'
import type { SyncBatchDetail } from '@/types/sync'
import type { FeishuFullImportPayload } from '@/types/system-import'

export function executeFeishuFullImport(payload: FeishuFullImportPayload) {
  return request.post<never, SyncBatchDetail>('/v1/system/imports/feishu/full', payload)
}

export function executeFeishuFullImportByUpload(file: File, importMode: string, remark?: string) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('importMode', importMode)
  if (remark) {
    formData.append('remark', remark)
  }
  return request.post<never, SyncBatchDetail>('/v1/system/imports/feishu/full/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}
