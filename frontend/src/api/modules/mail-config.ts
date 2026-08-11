import request from '@/api/request'
import type { MailConfig, MailConfigTestResult, SaveMailConfigPayload, TestMailConfigPayload } from '@/types/mail-config'

export function fetchMailConfig() {
  return request.get<never, MailConfig | null>('/v2/system/mail-config')
}

export function saveMailConfig(payload: SaveMailConfigPayload) {
  return request.put<never, MailConfig>('/v2/system/mail-config', payload)
}

export function testMailConfig(payload: TestMailConfigPayload) {
  return request.post<never, MailConfigTestResult>('/v2/system/mail-config/test', payload)
}
