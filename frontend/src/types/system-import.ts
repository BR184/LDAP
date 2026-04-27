export type ImportMode = 'SUPPLEMENT' | 'ALIGN'

export interface FeishuFullImportPayload {
  documentPath: string
  remark?: string
  importMode: ImportMode
}
