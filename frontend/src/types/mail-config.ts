export type MailSendMode = 'SMTP'

export type MailSecureMode = 'NONE' | 'STARTTLS' | 'SSL_TLS'

export interface MailConfig {
  id: number | null
  sendMode: MailSendMode
  secureMode: MailSecureMode
  host: string
  port: number
  fromAddress: string
  fromName: string | null
  authRequired: boolean
  username: string | null
  passwordConfigured: boolean
  enabled: boolean
  remark: string | null
  lastTestSuccess: boolean | null
  lastTestAt: string | null
  lastTestMessage: string | null
}

export interface SaveMailConfigPayload {
  sendMode: MailSendMode
  secureMode: MailSecureMode
  host: string
  port: number
  fromAddress: string
  fromName?: string
  authRequired: boolean
  username?: string
  password?: string
  enabled: boolean
  remark?: string
}

export interface TestMailConfigPayload {
  sendMode: MailSendMode
  secureMode: MailSecureMode
  host: string
  port: number
  fromAddress: string
  fromName?: string
  authRequired: boolean
  username?: string
  password?: string
  testToAddress: string
}

export interface MailConfigTestResult {
  success: boolean
  message: string
}
