import request from '@/api/request'
import type { MailConfig, MailConfigTestResult, SaveMailConfigPayload, TestMailConfigPayload } from '@/types/mail-config'

export function fetchMailConfig() {
  return request.get<never, MailConfig | null>('/v1/system/mail-config')
}

export function saveMailConfig(payload: SaveMailConfigPayload) {
  return request.put<never, MailConfig>('/v1/system/mail-config', payload)
}

export function testMailConfig(payload: TestMailConfigPayload) {
  return request.post<never, MailConfigTestResult>('/v1/system/mail-config/test', payload)
}
