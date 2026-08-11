import request from '@/api/request'
import type { ConfirmImportPlanPayload, FeishuFullImportPayload, ImportBatch, ImportBatchDetail, ImportPlanReview } from '@/types/system-import'

export function generateFeishuImportPlan(payload: FeishuFullImportPayload) {
  return request.post<never, ImportBatchDetail>('/v2/import/plan', payload)
}

export function generateFeishuImportPlanByUpload(file: File, remark?: string) {
  const formData = new FormData()
  formData.append('file', file)
  if (remark) {
    formData.append('remark', remark)
  }
  return request.post<never, ImportBatchDetail>('/v2/import/plan/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    timeout: 60000,
  })
}

export function fetchImportPlans(limit = 50) {
  return request.get<never, ImportBatch[]>('/v2/import/plan', { params: { limit } })
}

export function fetchImportPlanDetail(batchId: number) {
  return request.get<never, ImportBatchDetail>(`/v2/import/plan/${batchId}`)
}

export function fetchImportPlanReview(batchId: number) {
  return request.get<never, ImportPlanReview>(`/v2/import/plan/${batchId}/review`)
}

export function confirmImportPlan(batchId: number, payload: ConfirmImportPlanPayload) {
  return request.post<never, ImportBatchDetail>(`/v2/import/plan/${batchId}/confirm`, payload)
}

export function skipImportConflict(batchId: number, itemId: number) {
  return request.post<never, ImportBatchDetail>(`/v2/import/plan/${batchId}/conflicts/${itemId}/skip`)
}

export function mergeImportConflictByEmployeeNumber(batchId: number, itemId: number) {
  return request.post<never, ImportBatchDetail>(`/v2/import/plan/${batchId}/conflicts/${itemId}/merge-by-employee-no`)
}

export function cancelImportPlan(batchId: number) {
  return request.post<never, ImportBatchDetail>(`/v2/import/plan/${batchId}/cancel`)
}

export function executeImportPlan(batchId: number) {
  return request.post<never, ImportBatchDetail>(`/v2/import/plan/${batchId}/execute`, {}, {
    timeout: 180000,
  })
}

export function generateImportRollbackPlan(batchId: number) {
  return request.post<never, ImportBatchDetail>(`/v2/import/plan/${batchId}/rollback-plan`, {}, {
    timeout: 120000,
  })
}

export function rollbackImportPlan(batchId: number) {
  return request.post<never, ImportBatchDetail>(`/v2/import/plan/${batchId}/rollback`, {}, {
    timeout: 180000,
  })
}

export function retryImportLdapFailures(batchId: number) {
  return request.post<never, ImportBatchDetail>(`/v2/import/plan/${batchId}/retry-ldap`, {}, {
    timeout: 180000,
  })
}
