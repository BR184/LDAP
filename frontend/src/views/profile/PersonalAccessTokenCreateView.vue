<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  Check,
  Close,
  CopyDocument,
  Key,
  Search,
  Warning,
} from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  createPersonalAccessToken,
  fetchAvailableTokenPermissionGroups,
} from '@/api/modules/personal-access-token'
import type {
  CreatePersonalAccessTokenPayload,
  TokenPermissionGroup,
  TokenPermissionRisk,
} from '@/types/personal-access-token'

type ExpiryMode = '30' | '90' | '180' | '365' | 'CUSTOM' | 'NEVER'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const submitting = ref(false)
const groupKeyword = ref('')
const expandedGroups = ref<string[]>([])
const secretValue = ref('')
const createdName = ref('')
const customExpiry = ref<Date | null>(null)
const groups = ref<TokenPermissionGroup[]>([])

const form = reactive({
  name: '',
  description: '',
  expiryMode: '90' as ExpiryMode,
  scopeMode: 'FIXED' as CreatePersonalAccessTokenPayload['scopeMode'],
  permissionIds: [] as number[],
})

const expiryOptions = [
  { label: '30 天', value: '30' },
  { label: '90 天', value: '90' },
  { label: '180 天', value: '180' },
  { label: '365 天', value: '365' },
  { label: '自定义', value: 'CUSTOM' },
  { label: '永不过期', value: 'NEVER' },
]

const riskMeta: Record<TokenPermissionRisk, { title: string; description: string }> = {
  LOW: { title: '低风险权限', description: '查询与读取类接口，不改变平台数据。' },
  HIGH: { title: '高风险权限', description: '会修改数据、触发同步或影响账号安全，请谨慎授权。' },
}

const isFollowAccount = computed(() => form.scopeMode === 'FOLLOW_ACCOUNT')
const selectedCount = computed(() => form.permissionIds.length)

const filteredGroups = computed(() => {
  const keyword = groupKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return groups.value
  }
  return groups.value
    .map((group) => ({
      ...group,
      permissions: group.permissions.filter((permission) =>
        [permission.permissionName, permission.permissionCode, permission.resourcePath, permission.action]
          .some((value) => value.toLowerCase().includes(keyword)),
      ),
    }))
    .filter((group) => group.permissions.length > 0 || group.name.toLowerCase().includes(keyword))
})

const groupsByRisk = computed(() => ({
  LOW: filteredGroups.value.filter((group) => group.risk === 'LOW'),
  HIGH: filteredGroups.value.filter((group) => group.risk === 'HIGH'),
}))

const rules: FormRules<typeof form> = {
  name: [
    { required: true, message: '请输入密钥名称', trigger: 'blur' },
    { max: 64, message: '名称不能超过 64 个字符', trigger: 'blur' },
  ],
  description: [{ max: 255, message: '描述不能超过 255 个字符', trigger: 'blur' }],
}

onMounted(loadGroups)

async function loadGroups() {
  loading.value = true
  try {
    groups.value = await fetchAvailableTokenPermissionGroups()
  } finally {
    loading.value = false
  }
}

function groupChecked(group: TokenPermissionGroup) {
  return !isFollowAccount.value
    && group.permissions.length > 0
    && group.permissions.every((permission) => form.permissionIds.includes(permission.id))
}

function groupIndeterminate(group: TokenPermissionGroup) {
  if (isFollowAccount.value || group.permissions.length === 0) {
    return false
  }
  const selected = group.permissions.filter((permission) => form.permissionIds.includes(permission.id)).length
  return selected > 0 && selected < group.permissions.length
}

function toggleGroup(group: TokenPermissionGroup, checked: boolean | string | number) {
  if (isFollowAccount.value) {
    return
  }
  const selected = new Set(form.permissionIds)
  group.permissions.forEach((permission) => {
    if (checked) {
      selected.add(permission.id)
    } else {
      selected.delete(permission.id)
    }
  })
  form.permissionIds = [...selected]
}

function setScopeMode(value: boolean | string | number) {
  form.scopeMode = value ? 'FOLLOW_ACCOUNT' : 'FIXED'
  if (form.scopeMode === 'FOLLOW_ACCOUNT') {
    form.permissionIds = []
  }
}

function resolveExpiresAt() {
  if (form.expiryMode === 'NEVER') {
    return null
  }
  if (form.expiryMode === 'CUSTOM') {
    return customExpiry.value ? formatLocalDateTime(customExpiry.value) : null
  }
  const expiresAt = new Date()
  expiresAt.setDate(expiresAt.getDate() + Number(form.expiryMode))
  return formatLocalDateTime(expiresAt)
}

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (!isFollowAccount.value && form.permissionIds.length === 0) {
    ElMessage.warning('请至少选择一个权限组')
    return
  }
  const expiresAt = resolveExpiresAt()
  if (form.expiryMode === 'CUSTOM' && !expiresAt) {
    ElMessage.warning('请选择有效的过期时间')
    return
  }

  submitting.value = true
  try {
    const created = await createPersonalAccessToken({
      name: form.name.trim(),
      description: form.description.trim() || null,
      expiresAt,
      permissionIds: form.permissionIds,
      scopeMode: form.scopeMode,
    })
    secretValue.value = created.secret
    createdName.value = created.token.name
    ElMessage.success('访问密钥已创建')
  } finally {
    submitting.value = false
  }
}

async function copySecret() {
  if (!secretValue.value) {
    return
  }
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(secretValue.value)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = secretValue.value
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    ElMessage.success('完整密钥已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择密钥')
  }
}

function formatLocalDateTime(value: Date) {
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
}

function disablePastDate(value: Date) {
  return value.getTime() <= Date.now()
}

function goBack() {
  router.push('/access-tokens')
}
</script>

<template>
  <PageContainer title="创建访问密钥" description="为脚本、构建服务或内部工具创建可审计的 API 凭证。">
    <template #extra>
      <el-button :icon="ArrowLeft" text @click="goBack">返回密钥清单</el-button>
    </template>

    <div class="token-create-layout">
      <section class="token-create-main">
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <div class="section-heading">
            <span class="section-heading__index">01</span>
            <div>
              <h2>基本信息</h2>
              <p>使用清晰的名称，方便后续识别、轮换和审计。</p>
            </div>
          </div>
          <div class="field-grid">
            <el-form-item label="密钥名称" prop="name">
              <el-input v-model="form.name" maxlength="64" show-word-limit placeholder="例如：构建服务生产环境" />
            </el-form-item>
            <el-form-item label="描述" prop="description">
              <el-input v-model="form.description" maxlength="255" show-word-limit placeholder="说明用途、系统或负责人" />
            </el-form-item>
          </div>

          <div class="section-heading section-heading--spaced">
            <span class="section-heading__index">02</span>
            <div>
              <h2>生命周期</h2>
              <p>设置过期时间，并决定权限是否自动跟随账号变化。</p>
            </div>
          </div>
          <div class="lifecycle-row">
            <div class="lifecycle-field lifecycle-field--expiry">
              <span class="field-label">有效期</span>
              <el-segmented v-model="form.expiryMode" :options="expiryOptions" block />
              <el-date-picker
                v-if="form.expiryMode === 'CUSTOM'"
                v-model="customExpiry"
                class="custom-expiry"
                type="datetime"
                placeholder="选择过期时间"
                :disabled-date="disablePastDate"
              />
            </div>
            <div class="follow-control">
              <div>
                <span class="field-label">自动赋予新权限</span>
                <p>账号获得新的 API 权限后，密钥自动获得；权限被回收时立即失效。</p>
              </div>
              <el-switch
                :model-value="isFollowAccount"
                inline-prompt
                :active-icon="Check"
                :inactive-icon="Close"
                @update:model-value="setScopeMode"
              />
            </div>
          </div>
          <el-alert
            v-if="isFollowAccount"
            title="自动跟随已开启"
            description="此密钥将使用账号当前全部 API 权限，固定权限组选择已关闭。"
            type="info"
            :closable="false"
            show-icon
          />

          <div class="section-heading section-heading--spaced">
            <span class="section-heading__index">03</span>
            <div>
              <h2>权限范围</h2>
              <p>按权限组授权。组内成员仅用于查看，提交时会展开为稳定的权限 ID。</p>
            </div>
          </div>
          <div v-loading="loading" class="permission-scope">
            <div class="permission-scope__toolbar">
              <el-input v-model="groupKeyword" :prefix-icon="Search" clearable placeholder="搜索权限组、名称或编码" />
              <span>{{ isFollowAccount ? '自动跟随账号权限' : `已选择 ${selectedCount} 项 API 权限` }}</span>
            </div>
            <div v-for="risk in (['LOW', 'HIGH'] as TokenPermissionRisk[])" :key="risk" class="risk-section">
              <header :class="['risk-section__header', `risk-section__header--${risk.toLowerCase()}`]">
                <div>
                  <strong>{{ riskMeta[risk].title }}</strong>
                  <span>{{ riskMeta[risk].description }}</span>
                </div>
                <el-tag :type="risk === 'HIGH' ? 'danger' : 'success'" effect="plain">
                  {{ groupsByRisk[risk].length }} 组
                </el-tag>
              </header>
              <el-collapse v-model="expandedGroups" class="permission-groups">
                <el-collapse-item
                  v-for="group in groupsByRisk[risk]"
                  :key="group.id"
                  :name="group.id"
                  :disabled="isFollowAccount"
                >
                  <template #title>
                    <div class="group-title" @click.stop>
                      <el-checkbox
                        :model-value="groupChecked(group)"
                        :indeterminate="groupIndeterminate(group)"
                        :disabled="isFollowAccount"
                        @change="(value) => toggleGroup(group, value)"
                      />
                      <span>
                        <strong>{{ group.name }}</strong>
                        <small>{{ group.permissions.length }} 项 API 权限</small>
                      </span>
                    </div>
                  </template>
                  <div class="permission-members">
                    <div v-for="permission in group.permissions" :key="permission.id" class="permission-member">
                      <span>{{ permission.permissionName }}</span>
                      <code>{{ permission.permissionCode }}</code>
                    </div>
                  </div>
                </el-collapse-item>
              </el-collapse>
              <el-empty
                v-if="groupsByRisk[risk].length === 0"
                :image-size="56"
                description="没有匹配的权限组"
              />
            </div>
          </div>

          <div class="form-actions">
            <el-button @click="goBack">取消</el-button>
            <el-button type="primary" :loading="submitting" :icon="Key" @click="handleCreate">
              创建访问密钥
            </el-button>
          </div>
        </el-form>
      </section>

      <aside class="token-create-aside">
        <div class="aside-block aside-block--quiet">
          <span class="aside-kicker">SECURITY NOTE</span>
          <h2>一份密钥，一个清晰边界</h2>
          <p>密钥始终受账号状态、角色权限和有效期约束。撤销或删除后，已发出的调用会立即失效。</p>
        </div>
        <div v-if="secretValue" class="aside-block secret-block">
          <div class="secret-block__heading">
            <span class="secret-block__icon"><el-icon><Check /></el-icon></span>
            <div>
              <span class="aside-kicker">CREATED</span>
              <h2>{{ createdName }}</h2>
            </div>
          </div>
          <el-alert
            title="密钥已生成，可随时复制"
            description="完整密钥仅保存在当前页面内存中；离开此页后请从清单执行查看。"
            type="success"
            :closable="false"
            show-icon
          />
          <div class="secret-value">
            <code>{{ secretValue }}</code>
            <el-tooltip content="复制完整密钥" placement="top">
              <el-button :icon="CopyDocument" circle @click="copySecret" />
            </el-tooltip>
          </div>
          <el-button class="secret-block__back" text @click="goBack">返回密钥清单</el-button>
        </div>
        <div v-else class="aside-block aside-block--guide">
          <el-icon><Warning /></el-icon>
          <div>
            <strong>高风险权限需要明确授权</strong>
            <p>默认按组收起，展开后可查看每个 API 成员。</p>
          </div>
        </div>
      </aside>
    </div>
  </PageContainer>
</template>

<style scoped lang="scss">
.token-create-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 24px;
  align-items: start;
}

.token-create-main,
.aside-block {
  border: 1px solid rgba(109, 130, 126, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 16px 38px rgba(30, 53, 50, 0.06);
  backdrop-filter: blur(16px);
}

.token-create-main {
  padding: 28px 32px 24px;
}

.section-heading {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.section-heading--spaced {
  margin-top: 34px;
}

.section-heading__index {
  color: #15736b;
  font-family: Consolas, monospace;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.8;
}

.section-heading h2,
.aside-block h2 {
  margin: 0;
  color: #1f2d2a;
  font-size: 17px;
  font-weight: 650;
}

.section-heading p,
.aside-block p {
  margin: 5px 0 0;
  color: #75837f;
  font-size: 12px;
  line-height: 1.6;
}

.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  margin-top: 20px;
}

.lifecycle-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 0.8fr);
  gap: 20px;
  align-items: start;
  margin-top: 20px;
}

.field-label {
  display: block;
  margin-bottom: 9px;
  color: #3e504b;
  font-size: 13px;
  font-weight: 600;
}

.custom-expiry {
  width: 100%;
  margin-top: 12px;
}

.follow-control {
  display: flex;
  min-height: 76px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 16px;
  border: 1px solid #d9e5e2;
  border-radius: 8px;
  background: #f7faf9;
}

.follow-control p {
  max-width: 270px;
  margin: 4px 0 0;
  color: #788681;
  font-size: 12px;
  line-height: 1.5;
}

.permission-scope {
  margin-top: 18px;
  border: 1px solid #dbe5e2;
  border-radius: 8px;
  background: #fbfcfc;
}

.permission-scope__toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px;
  border-bottom: 1px solid #e4ecea;
}

.permission-scope__toolbar .el-input {
  max-width: 380px;
}

.permission-scope__toolbar span {
  margin-left: auto;
  color: #667773;
  font-size: 12px;
}

.risk-section {
  padding: 14px 16px 0;
}

.risk-section:last-child {
  padding-bottom: 16px;
}

.risk-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
}

.risk-section__header > div {
  display: grid;
  gap: 3px;
}

.risk-section__header strong {
  color: #31433f;
  font-size: 13px;
}

.risk-section__header span {
  color: #7b8985;
  font-size: 12px;
}

.risk-section__header--high strong {
  color: #8e4e40;
}

.permission-groups {
  border-top: 1px solid #e5ecea;
}

.group-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.group-title > span {
  display: grid;
  gap: 2px;
}

.group-title strong {
  color: #2c3d39;
  font-size: 13px;
  font-weight: 600;
}

.group-title small {
  color: #899590;
  font-size: 11px;
}

.permission-members {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px 18px;
  padding: 2px 14px 14px 42px;
}

.permission-member {
  display: flex;
  min-width: 0;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #edf1f0;
}

.permission-member span,
.permission-member code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-member span {
  color: #4b5b56;
  font-size: 12px;
}

.permission-member code {
  color: #8a9692;
  font-family: Consolas, monospace;
  font-size: 11px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 28px;
  padding-top: 18px;
  border-top: 1px solid #e5ecea;
}

.token-create-aside {
  display: grid;
  gap: 16px;
}

.aside-block {
  padding: 20px;
}

.aside-kicker {
  display: block;
  margin-bottom: 8px;
  color: #81908b;
  font-family: Consolas, monospace;
  font-size: 10px;
  letter-spacing: 0.08em;
}

.aside-block--quiet {
  background: rgba(244, 248, 247, 0.9);
}

.aside-block--guide {
  display: flex;
  gap: 12px;
  color: #8e4e40;
}

.aside-block--guide > .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
  font-size: 18px;
}

.aside-block--guide strong {
  color: #6f4c43;
  font-size: 13px;
}

.secret-block {
  border-color: rgba(21, 115, 107, 0.3);
  background: rgba(241, 249, 246, 0.96);
}

.secret-block__heading {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.secret-block__heading h2 {
  font-size: 15px;
}

.secret-block__icon {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: #18856f;
}

.secret-value {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  margin-top: 14px;
  padding: 10px;
  border: 1px solid #c5ddd6;
  border-radius: 6px;
  background: #fff;
}

.secret-value code {
  overflow-wrap: anywhere;
  color: #17483f;
  font-family: Consolas, monospace;
  font-size: 11px;
  line-height: 1.5;
  user-select: all;
}

.secret-block__back {
  margin-top: 8px;
  padding-left: 0;
}

:deep(.el-alert) {
  margin-top: 14px;
}

:deep(.el-collapse-item__header) {
  height: 48px;
  color: #3e504b;
  font-size: 13px;
}

:deep(.el-collapse-item__wrap) {
  background: transparent;
}

:deep(.el-segmented) {
  --el-segmented-item-selected-color: #155e57;
  --el-segmented-item-selected-bg-color: #e5f1ee;
}

@media (prefers-reduced-motion: reduce) {
  .token-create-main,
  .aside-block {
    transition: none;
  }
}
</style>
