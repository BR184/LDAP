<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Check, CopyDocument, Key, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createPersonalAccessToken,
  fetchAvailableTokenPermissions,
  fetchPersonalAccessTokens,
  revokePersonalAccessToken,
} from '@/api/modules/personal-access-token'
import { verifyMyPassword } from '@/api/modules/user'
import type {
  PersonalAccessTokenItem,
  PersonalAccessTokenStatus,
  TokenPermission,
} from '@/types/personal-access-token'

type ExpiryMode = '30' | '90' | '180' | '365' | 'CUSTOM' | 'NEVER'

const tokens = ref<PersonalAccessTokenItem[]>([])
const availablePermissions = ref<TokenPermission[]>([])
const loading = ref(false)
const permissionsLoading = ref(false)
const createSubmitting = ref(false)
const createVisible = ref(false)
const secretVisible = ref(false)
const secretValue = ref('')
const createdTokenName = ref('')
const permissionKeyword = ref('')
const customExpiry = ref<Date | null>(null)
const createFormRef = ref<FormInstance>()
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const createForm = reactive({
  name: '',
  expiryMode: '90' as ExpiryMode,
  permissionIds: [] as number[],
  oldPassword: '',
})

const expiryOptions = [
  { label: '30 天', value: '30' },
  { label: '90 天', value: '90' },
  { label: '180 天', value: '180' },
  { label: '365 天', value: '365' },
  { label: '自定义', value: 'CUSTOM' },
  { label: '永不过期', value: 'NEVER' },
]

const statusMeta: Record<PersonalAccessTokenStatus, { label: string; type: 'success' | 'warning' | 'info' }> = {
  ACTIVE: { label: '有效', type: 'success' },
  EXPIRED: { label: '已过期', type: 'warning' },
  REVOKED: { label: '已撤销', type: 'info' },
}

const filteredPermissions = computed(() => {
  const keyword = permissionKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return availablePermissions.value
  }
  return availablePermissions.value.filter((permission) =>
    [permission.permissionName, permission.permissionCode, permission.resourcePath, permission.action]
      .some((value) => value.toLowerCase().includes(keyword)),
  )
})

const activeCount = computed(() => tokens.value.filter((token) => token.status === 'ACTIVE').length)

const createRules: FormRules<typeof createForm> = {
  name: [
    { required: true, message: '请输入密钥名称', trigger: 'blur' },
    { max: 64, message: '名称不能超过 64 个字符', trigger: 'blur' },
  ],
  permissionIds: [{ type: 'array', required: true, min: 1, message: '请至少选择一项权限', trigger: 'change' }],
  oldPassword: [{ required: true, message: '请输入当前登录密码', trigger: 'blur' }],
}

onMounted(async () => {
  await Promise.all([loadTokens(), loadPermissions()])
})

async function loadTokens() {
  loading.value = true
  try {
    const result = await fetchPersonalAccessTokens(pagination.page, pagination.pageSize)
    tokens.value = result.items
    pagination.total = result.total
    pagination.page = result.page
    pagination.pageSize = result.pageSize
  } finally {
    loading.value = false
  }
}

async function loadPermissions() {
  permissionsLoading.value = true
  try {
    availablePermissions.value = await fetchAvailableTokenPermissions()
  } finally {
    permissionsLoading.value = false
  }
}

function openCreateDialog() {
  createForm.name = ''
  createForm.expiryMode = '90'
  createForm.permissionIds = []
  createForm.oldPassword = ''
  customExpiry.value = null
  permissionKeyword.value = ''
  createVisible.value = true
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  const expiresAt = resolveExpiresAt()
  if (createForm.expiryMode === 'CUSTOM' && !expiresAt) {
    ElMessage.warning('请选择有效的过期时间')
    return
  }

  createSubmitting.value = true
  try {
    const verification = await verifyMyPassword({ oldPassword: createForm.oldPassword })
    const created = await createPersonalAccessToken({
      name: createForm.name.trim(),
      expiresAt,
      permissionIds: createForm.permissionIds,
      passwordVerificationToken: verification.verificationToken,
    })
    secretValue.value = created.secret
    createdTokenName.value = created.token.name
    createForm.oldPassword = ''
    createVisible.value = false
    secretVisible.value = true
    await loadTokens()
  } finally {
    createSubmitting.value = false
  }
}

async function handleRevoke(token: PersonalAccessTokenItem) {
  await ElMessageBox.confirm(`撤销“${token.name}”后，使用该密钥的调用将立即失败。`, '撤销访问密钥', {
    type: 'warning',
    confirmButtonText: '撤销',
    cancelButtonText: '取消',
    confirmButtonClass: 'el-button--danger',
  })
  await revokePersonalAccessToken(token.id)
  ElMessage.success('访问密钥已撤销')
  await loadTokens()
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
    ElMessage.success('密钥已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择密钥')
  }
}

function closeSecretDialog() {
  secretVisible.value = false
  secretValue.value = ''
  createdTokenName.value = ''
}

function resolveExpiresAt() {
  if (createForm.expiryMode === 'NEVER') {
    return null
  }
  if (createForm.expiryMode === 'CUSTOM') {
    return customExpiry.value ? formatLocalDateTime(customExpiry.value) : null
  }
  const expiresAt = new Date()
  expiresAt.setDate(expiresAt.getDate() + Number(createForm.expiryMode))
  return formatLocalDateTime(expiresAt)
}

function formatLocalDateTime(value: Date) {
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
}

function formatDateTime(value: string | null) {
  if (!value) {
    return '从未使用'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function formatExpiry(value: string | null) {
  return value ? formatDateTime(value) : '永不过期'
}

function disablePastDate(value: Date) {
  return value.getTime() <= Date.now()
}
</script>

<template>
  <PageContainer title="访问密钥">
    <template #extra>
      <el-button class="create-token-button" type="primary" :icon="Plus" @click="openCreateDialog">
        创建密钥
      </el-button>
    </template>

    <section class="token-overview">
      <div class="token-overview__identity">
        <span class="token-overview__icon"><el-icon><Key /></el-icon></span>
        <div>
          <span class="token-overview__eyebrow">API CREDENTIALS</span>
          <strong>个人访问密钥</strong>
        </div>
      </div>
      <div class="token-overview__metrics">
        <div>
          <span>当前页有效</span>
          <strong>{{ activeCount }}</strong>
        </div>
        <div>
          <span>密钥总数</span>
          <strong>{{ pagination.total }}</strong>
        </div>
        <div>
          <span>可选权限</span>
          <strong>{{ availablePermissions.length }}</strong>
        </div>
      </div>
    </section>

    <section class="token-surface" aria-label="访问密钥列表">
      <header class="token-surface__toolbar">
        <div>
          <h2>密钥清单</h2>
          <span>{{ pagination.total }} 项</span>
        </div>
        <el-tooltip content="刷新" placement="top">
          <el-button :icon="Refresh" circle text :loading="loading" @click="loadTokens" />
        </el-tooltip>
      </header>

      <el-table v-loading="loading" :data="tokens" class="token-table" empty-text="暂无访问密钥">
        <el-table-column label="名称与前缀" min-width="230">
          <template #default="{ row }: { row: PersonalAccessTokenItem }">
            <div class="token-name-cell">
              <strong>{{ row.name }}</strong>
              <code>{{ row.tokenPrefix }}</code>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }: { row: PersonalAccessTokenItem }">
            <el-tag :type="statusMeta[row.status].type" effect="light" round>
              {{ statusMeta[row.status].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限范围" min-width="280">
          <template #default="{ row }: { row: PersonalAccessTokenItem }">
            <div class="permission-summary">
              <el-tag v-for="permission in row.permissions.slice(0, 2)" :key="permission.id" effect="plain">
                {{ permission.permissionName }}
              </el-tag>
              <span v-if="row.permissions.length > 2">+{{ row.permissions.length - 2 }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="有效期" min-width="168">
          <template #default="{ row }: { row: PersonalAccessTokenItem }">
            {{ formatExpiry(row.expiresAt) }}
          </template>
        </el-table-column>
        <el-table-column label="最近使用" min-width="188">
          <template #default="{ row }: { row: PersonalAccessTokenItem }">
            <div class="last-used-cell">
              <span>{{ formatDateTime(row.lastUsedAt) }}</span>
              <small v-if="row.lastUsedIp">{{ row.lastUsedIp }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="96" fixed="right" align="right">
          <template #default="{ row }: { row: PersonalAccessTokenItem }">
            <el-button v-if="row.status === 'ACTIVE'" type="danger" link @click="handleRevoke(row)">
              撤销
            </el-button>
            <span v-else class="action-placeholder">--</span>
          </template>
        </el-table-column>
      </el-table>

      <footer v-if="pagination.total > pagination.pageSize" class="token-surface__footer">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadTokens"
          @size-change="loadTokens"
        />
      </footer>
    </section>

    <el-dialog v-model="createVisible" title="创建访问密钥" width="760px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
        <div class="create-grid">
          <el-form-item label="密钥名称" prop="name">
            <el-input v-model="createForm.name" maxlength="64" show-word-limit placeholder="例如：构建服务" />
          </el-form-item>
          <el-form-item label="当前登录密码" prop="oldPassword">
            <el-input v-model="createForm.oldPassword" type="password" show-password autocomplete="current-password" />
          </el-form-item>
        </div>

        <el-form-item label="有效期">
          <el-segmented v-model="createForm.expiryMode" :options="expiryOptions" block />
          <el-date-picker
            v-if="createForm.expiryMode === 'CUSTOM'"
            v-model="customExpiry"
            class="custom-expiry"
            type="datetime"
            placeholder="选择过期时间"
            :disabled-date="disablePastDate"
          />
        </el-form-item>

        <el-form-item label="权限范围" prop="permissionIds">
          <div class="permission-picker">
            <el-input v-model="permissionKeyword" :prefix-icon="Search" clearable placeholder="搜索权限名称或编码" />
            <el-scrollbar v-loading="permissionsLoading" height="286px" class="permission-picker__list">
              <el-checkbox-group v-model="createForm.permissionIds">
                <label v-for="permission in filteredPermissions" :key="permission.id" class="permission-option">
                  <el-checkbox :value="permission.id" />
                  <span class="permission-option__content">
                    <strong>{{ permission.permissionName }}</strong>
                    <code>{{ permission.permissionCode }}</code>
                    <small>{{ permission.action }} {{ permission.resourcePath }}</small>
                  </span>
                </label>
              </el-checkbox-group>
              <el-empty v-if="!permissionsLoading && filteredPermissions.length === 0" :image-size="64" description="未找到权限" />
            </el-scrollbar>
            <span class="permission-picker__count">已选择 {{ createForm.permissionIds.length }} 项</span>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="handleCreate">创建密钥</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="secretVisible"
      title="密钥已创建"
      width="640px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="secret-result">
        <span class="secret-result__success"><el-icon><Check /></el-icon></span>
        <div>
          <strong>{{ createdTokenName }}</strong>
          <p>完整密钥只显示一次，关闭后无法再次查看。</p>
        </div>
      </div>
      <div class="secret-value">
        <code>{{ secretValue }}</code>
        <el-tooltip content="复制密钥" placement="top">
          <el-button :icon="CopyDocument" circle @click="copySecret" />
        </el-tooltip>
      </div>
      <template #footer>
        <el-button type="primary" @click="closeSecretDialog">我已保存</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<style scoped lang="scss">
.create-token-button,
.token-surface,
.token-overview {
  --el-color-primary: #15736b;
  --el-color-primary-light-3: #4f978f;
  --el-color-primary-light-5: #7fb4ae;
  --el-color-primary-light-8: #d5e8e5;
  --el-color-primary-light-9: #edf6f4;
  --el-color-primary-dark-2: #0e5c56;
}

.token-overview {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) auto;
  align-items: center;
  gap: 32px;
  min-height: 124px;
  padding: 22px 26px;
  overflow: hidden;
  border: 1px solid rgba(109, 130, 126, 0.2);
  border-radius: 8px;
  background: rgba(250, 252, 251, 0.76);
  box-shadow: 0 18px 45px rgba(30, 53, 50, 0.08);
  backdrop-filter: blur(18px) saturate(1.2);
  animation: workspace-enter 520ms cubic-bezier(0.16, 1, 0.3, 1) both;
}

.token-overview__identity {
  display: flex;
  align-items: center;
  gap: 16px;

  strong {
    display: block;
    margin-top: 4px;
    color: #172522;
    font-size: 21px;
    font-weight: 650;
    letter-spacing: 0;
  }
}

.token-overview__icon {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border: 1px solid rgba(21, 115, 107, 0.22);
  border-radius: 8px;
  color: #15736b;
  background: rgba(223, 240, 236, 0.7);
  font-size: 23px;
}

.token-overview__eyebrow {
  color: #6f7f7c;
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: 11px;
  letter-spacing: 0;
}

.token-overview__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(92px, 1fr));
  gap: 1px;
  overflow: hidden;
  border: 1px solid rgba(109, 130, 126, 0.18);
  border-radius: 8px;
  background: rgba(109, 130, 126, 0.16);

  div {
    display: flex;
    min-width: 104px;
    padding: 14px 16px;
    flex-direction: column;
    gap: 6px;
    background: rgba(255, 255, 255, 0.8);
  }

  span {
    color: #788683;
    font-size: 12px;
  }

  strong {
    color: #172522;
    font-size: 22px;
    font-weight: 650;
    font-variant-numeric: tabular-nums;
  }
}

.token-surface {
  overflow: hidden;
  border: 1px solid rgba(109, 130, 126, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 18px 42px rgba(30, 53, 50, 0.07);
  backdrop-filter: blur(16px);
  animation: workspace-enter 600ms 80ms cubic-bezier(0.16, 1, 0.3, 1) both;
}

.token-surface__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(109, 130, 126, 0.14);

  div {
    display: flex;
    align-items: baseline;
    gap: 10px;
  }

  h2 {
    margin: 0;
    color: #1d2c29;
    font-size: 16px;
    font-weight: 650;
    letter-spacing: 0;
  }

  span {
    color: #8a9693;
    font-size: 12px;
  }
}

.token-table {
  --el-table-border-color: rgba(109, 130, 126, 0.12);
  --el-table-header-bg-color: rgba(242, 246, 245, 0.82);
  --el-table-row-hover-bg-color: rgba(237, 246, 244, 0.72);
  width: 100%;
  border-radius: 0;

  :deep(th.el-table__cell) {
    height: 46px;
    color: #65736f;
    font-size: 12px;
    font-weight: 600;
  }

  :deep(td.el-table__cell) {
    padding: 14px 0;
  }
}

.token-name-cell,
.last-used-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;

  strong {
    overflow: hidden;
    color: #1f2d2a;
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  code,
  small {
    color: #7b8985;
    font-size: 12px;
  }

  code {
    font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  }
}

.permission-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;

  :deep(.el-tag) {
    max-width: 112px;
    border-color: rgba(21, 115, 107, 0.2);
    color: #315b56;
    background: rgba(237, 246, 244, 0.7);
  }

  span {
    color: #778480;
    font-size: 12px;
  }
}

.action-placeholder {
  color: #abb4b2;
}

.token-surface__footer {
  display: flex;
  justify-content: flex-end;
  padding: 14px 20px;
  border-top: 1px solid rgba(109, 130, 126, 0.12);
}

.create-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.custom-expiry {
  width: 100%;
  margin-top: 12px;
}

.permission-picker {
  width: 100%;
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: #fff;

  > .el-input {
    padding: 10px 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
}

.permission-picker__list {
  padding: 4px 12px;
}

.permission-option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 11px 4px;
  border-bottom: 1px solid #eef1f0;
  cursor: pointer;
  transition: background-color 160ms ease;

  &:hover {
    background: #f5f9f8;
  }

  > .el-checkbox {
    margin-top: 2px;
  }
}

.permission-option__content {
  display: grid;
  min-width: 0;
  flex: 1;
  grid-template-columns: minmax(130px, 0.8fr) minmax(190px, 1fr) minmax(220px, 1.2fr);
  align-items: center;
  gap: 10px;

  strong {
    color: #263531;
    font-size: 13px;
    font-weight: 600;
  }

  code,
  small {
    overflow: hidden;
    color: #74827e;
    font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
    font-size: 11px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.permission-picker__count {
  display: block;
  padding: 9px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: #64736f;
  background: #f7f9f8;
  font-size: 12px;
  text-align: right;
}

.secret-result {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;

  strong {
    color: #21312d;
    font-size: 16px;
  }

  p {
    margin: 5px 0 0;
    color: #73817d;
    font-size: 13px;
  }
}

.secret-result__success {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: #18856f;
  font-size: 21px;
}

.secret-value {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid #c9d9d5;
  border-radius: 8px;
  background: #f4f8f7;

  code {
    overflow-wrap: anywhere;
    color: #163d37;
    font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
    font-size: 13px;
    line-height: 1.6;
    user-select: all;
  }
}

@keyframes workspace-enter {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.99);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: 1080px) {
  .token-overview {
    grid-template-columns: 1fr;
  }

  .token-overview__metrics {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .token-overview,
  .token-surface {
    animation: none;
  }
}
</style>
