<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Connection, Promotion } from '@element-plus/icons-vue'
import { fetchMailConfig, saveMailConfig, testMailConfig } from '@/api/modules/mail-config'
import type { MailConfig, MailSecureMode, MailSendMode, SaveMailConfigPayload, TestMailConfigPayload } from '@/types/mail-config'

interface MailConfigForm {
  sendMode: MailSendMode
  secureMode: MailSecureMode
  fromLocalPart: string
  fromDomain: string
  fromName: string
  host: string
  port: number
  authRequired: boolean
  username: string
  password: string
  enabled: boolean
  remark: string
  testToAddress: string
}

const formRef = ref<FormInstance>()
const passwordConfigured = ref(false)

const form = reactive<MailConfigForm>({
  sendMode: 'SMTP',
  secureMode: 'STARTTLS',
  fromLocalPart: '',
  fromDomain: '',
  fromName: 'CrownCAD',
  host: '',
  port: 587,
  authRequired: true,
  username: '',
  password: '',
  enabled: true,
  remark: '',
  testToAddress: '',
})

const rules: FormRules<MailConfigForm> = {
  sendMode: [{ required: true, message: '请选择发送模式', trigger: 'change' }],
  secureMode: [{ required: true, message: '请选择加密方式', trigger: 'change' }],
  fromLocalPart: [{ required: true, message: '请输入发件邮箱前缀', trigger: 'blur' }],
  fromDomain: [{ required: true, message: '请输入发件邮箱域名', trigger: 'blur' }],
  host: [{ required: true, message: '请输入 SMTP 服务器地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入 SMTP 端口', trigger: 'blur' }],
  testToAddress: [{ type: 'email', message: '请输入合法测试收件邮箱', trigger: 'blur' }],
}

const currentConfigQuery = useQuery({
  queryKey: ['system', 'mail-config'],
  queryFn: fetchMailConfig,
})

const saveMutation = useMutation({
  mutationFn: saveMailConfig,
  onSuccess: (data) => {
    applyConfig(data)
    ElMessage.success('邮件配置已保存')
  },
})

const testMutation = useMutation({
  mutationFn: testMailConfig,
  onSuccess: (data) => {
    ElMessage.success(data.message)
  },
})

function parseAddress(address: string | null | undefined) {
  const normalized = (address || '').trim()
  if (!normalized || !normalized.includes('@')) {
    return { localPart: normalized, domain: '' }
  }
  const atIndex = normalized.indexOf('@')
  return {
    localPart: normalized.slice(0, atIndex),
    domain: normalized.slice(atIndex + 1),
  }
}

function buildFromAddress() {
  const localPart = form.fromLocalPart.trim()
  const domain = form.fromDomain.trim()
  return localPart && domain ? `${localPart}@${domain}` : ''
}

function applyConfig(data: MailConfig | null | undefined) {
  if (!data) {
    passwordConfigured.value = false
    return
  }
  const address = parseAddress(data.fromAddress)
  form.sendMode = data.sendMode || 'SMTP'
  form.secureMode = data.secureMode || 'STARTTLS'
  form.fromLocalPart = address.localPart
  form.fromDomain = address.domain
  form.fromName = data.fromName || 'CrownCAD'
  form.host = data.host || ''
  form.port = data.port || 587
  form.authRequired = data.authRequired ?? true
  form.username = data.username || ''
  form.password = ''
  form.enabled = data.enabled ?? true
  form.remark = data.remark || ''
  passwordConfigured.value = !!data.passwordConfigured
}

watch(
  () => currentConfigQuery.data.value,
  (data) => {
    applyConfig(data)
  },
  { immediate: true },
)

function buildSavePayload(): SaveMailConfigPayload {
  return {
    sendMode: form.sendMode,
    secureMode: form.secureMode,
    host: form.host.trim(),
    port: Number(form.port),
    fromAddress: buildFromAddress(),
    fromName: form.fromName.trim() || undefined,
    authRequired: form.authRequired,
    username: form.authRequired ? form.username.trim() : undefined,
    password: form.password.trim() || undefined,
    enabled: form.enabled,
    remark: form.remark.trim() || undefined,
  }
}

function buildTestPayload(): TestMailConfigPayload {
  return {
    sendMode: form.sendMode,
    secureMode: form.secureMode,
    host: form.host.trim(),
    port: Number(form.port),
    fromAddress: buildFromAddress(),
    fromName: form.fromName.trim() || undefined,
    authRequired: form.authRequired,
    username: form.authRequired ? form.username.trim() : undefined,
    password: form.password.trim() || undefined,
    testToAddress: form.testToAddress.trim(),
  }
}

async function validateBaseForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return false
  }
  if (form.authRequired && !form.username.trim()) {
    ElMessage.warning('启用认证时必须填写邮箱账号')
    return false
  }
  if (form.authRequired && !form.password.trim() && !passwordConfigured.value) {
    ElMessage.warning('启用认证时必须填写邮箱密码或授权码')
    return false
  }
  return true
}

async function handleSave() {
  const valid = await validateBaseForm()
  if (!valid) {
    return
  }
  await saveMutation.mutateAsync(buildSavePayload())
}

async function handleTest() {
  const valid = await validateBaseForm()
  if (!valid) {
    return
  }
  if (!form.testToAddress.trim()) {
    ElMessage.warning('请输入测试收件邮箱')
    return
  }
  await testMutation.mutateAsync(buildTestPayload())
}
</script>

<template>
  <PageContainer title="邮件配置" description="集中维护密码重置邮件的 SMTP 服务器、认证信息与测试发信能力。">
    <div class="mail-config-layout">
      <el-card class="idm-card" shadow="never">
        <template #header>
          <div class="section-header">
            <strong>配置说明</strong>
            <span class="idm-muted">保存后将优先使用后台邮件配置发送忘记密码和重置密码通知。</span>
          </div>
        </template>

        <div class="tips-grid">
          <el-alert :closable="false" show-icon title="推荐方式">
            内网 SMTP 建议优先使用 <strong>STARTTLS + 587</strong>，并使用完整邮箱账号作为认证用户名。
          </el-alert>
          <el-alert :closable="false" show-icon type="warning" title="安全提醒">
            邮箱密码或授权码不会明文回显；若页面提示“已配置”，表示系统中已保存密文。
          </el-alert>
        </div>
      </el-card>

      <el-card class="idm-card" shadow="never">
        <template #header>
          <div class="section-header">
            <strong>邮件服务器</strong>
            <span class="idm-muted">支持管理员在系统管理中自定义 SMTP 参数并即时测试。</span>
          </div>
        </template>

        <el-skeleton v-if="currentConfigQuery.isLoading.value" animated :rows="8" />

        <el-form v-else ref="formRef" :model="form" :rules="rules" label-position="top">
          <div class="form-grid form-grid--2">
            <el-form-item label="发送模式" prop="sendMode">
              <el-select v-model="form.sendMode" placeholder="请选择发送模式" style="width: 100%">
                <el-option label="SMTP" value="SMTP" />
              </el-select>
            </el-form-item>

            <el-form-item label="加密方式" prop="secureMode">
              <el-select v-model="form.secureMode" placeholder="请选择加密方式" style="width: 100%">
                <el-option label="无加密" value="NONE" />
                <el-option label="STARTTLS" value="STARTTLS" />
                <el-option label="SSL/TLS" value="SSL_TLS" />
              </el-select>
            </el-form-item>
          </div>

          <el-form-item label="来自地址">
            <div class="address-row">
              <el-form-item class="address-row__field" prop="fromLocalPart">
                <el-input v-model="form.fromLocalPart" placeholder="例如 huayun" />
              </el-form-item>
              <span class="address-row__at">@</span>
              <el-form-item class="address-row__field" prop="fromDomain">
                <el-input v-model="form.fromDomain" placeholder="例如 crowncad.com" />
              </el-form-item>
            </div>
          </el-form-item>

          <div class="form-grid form-grid--2">
            <el-form-item label="发件人名称">
              <el-input v-model="form.fromName" placeholder="例如 CrownCAD" />
            </el-form-item>

            <el-form-item label="服务器地址" prop="host">
              <el-input v-model="form.host" placeholder="例如 10.0.100.49" />
            </el-form-item>
          </div>

          <div class="form-grid form-grid--2">
            <el-form-item label="端口" prop="port">
              <el-input-number v-model="form.port" :max="65535" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>

            <el-form-item label="状态">
              <el-switch
                v-model="form.enabled"
                active-text="启用"
                inactive-text="停用"
              />
            </el-form-item>
          </div>

          <el-form-item>
            <el-checkbox v-model="form.authRequired">需要认证</el-checkbox>
          </el-form-item>

          <div class="form-grid form-grid--2">
            <el-form-item label="邮箱账号">
              <el-input
                v-model="form.username"
                :disabled="!form.authRequired"
                placeholder="例如 huayun@crowncad.com"
              />
            </el-form-item>

            <el-form-item :label="passwordConfigured ? '邮箱密码 / 授权码（留空则保持原值）' : '邮箱密码 / 授权码'">
              <el-input
                v-model="form.password"
                :disabled="!form.authRequired"
                placeholder="请输入邮箱密码或授权码"
                show-password
              />
            </el-form-item>
          </div>

          <el-form-item label="备注">
            <el-input v-model="form.remark" placeholder="可选，例如 CrownCAD 内网邮件配置" />
          </el-form-item>

          <el-divider />

          <div class="form-grid form-grid--2">
            <el-form-item label="测试收件邮箱" prop="testToAddress">
              <el-input v-model="form.testToAddress" placeholder="请输入测试收件邮箱" />
            </el-form-item>

            <el-form-item label="最近测试结果">
              <el-input
                :model-value="currentConfigQuery.data.value?.lastTestMessage || (passwordConfigured ? '已配置，可直接发送测试邮件' : '尚未配置')"
                disabled
              />
            </el-form-item>
          </div>

          <div class="form-actions">
            <el-button
              type="primary"
              :icon="Connection"
              :loading="saveMutation.isPending.value"
              @click="handleSave"
            >
              保存配置
            </el-button>
            <el-button
              type="success"
              plain
              :icon="Promotion"
              :loading="testMutation.isPending.value"
              @click="handleTest"
            >
              发送测试邮件
            </el-button>
          </div>
        </el-form>
      </el-card>
    </div>
  </PageContainer>
</template>

<style scoped lang="scss">
.mail-config-layout {
  display: grid;
  gap: 16px;
}

.section-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tips-grid {
  display: grid;
  gap: 16px;
}

.form-grid {
  display: grid;
  gap: 16px;
}

.form-grid--2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.address-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.address-row__field {
  margin-bottom: 0;
}

.address-row__at {
  color: var(--idm-text-secondary);
  font-weight: 600;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 960px) {
  .form-grid--2 {
    grid-template-columns: 1fr;
  }

  .address-row {
    grid-template-columns: 1fr;
  }

  .address-row__at {
    display: none;
  }

  .form-actions {
    flex-direction: column;
  }
}
</style>
