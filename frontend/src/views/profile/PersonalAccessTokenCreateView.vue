<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  Check,
  Close,
  Key,
  Search,
  Setting,
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
const permissionDialogVisible = ref(false)
const permissionDraftIds = ref<number[]>([])
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
const draftSelectedCount = computed(() => permissionDraftIds.value.length)

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
  return group.permissions.length > 0
    && group.permissions.every((permission) => permissionDraftIds.value.includes(permission.id))
}

function groupIndeterminate(group: TokenPermissionGroup) {
  if (group.permissions.length === 0) {
    return false
  }
  const selected = group.permissions.filter((permission) => permissionDraftIds.value.includes(permission.id)).length
  return selected > 0 && selected < group.permissions.length
}

function permissionChecked(permissionId: number) {
  return permissionDraftIds.value.includes(permissionId)
}

function togglePermission(permissionId: number, checked: boolean | string | number) {
  const selected = new Set(permissionDraftIds.value)
  if (checked) {
    selected.add(permissionId)
  } else {
    selected.delete(permissionId)
  }
  permissionDraftIds.value = [...selected].sort((left, right) => left - right)
}

function toggleGroup(group: TokenPermissionGroup, checked: boolean | string | number) {
  const selected = new Set(permissionDraftIds.value)
  group.permissions.forEach((permission) => {
    if (checked) {
      selected.add(permission.id)
    } else {
      selected.delete(permission.id)
    }
  })
  permissionDraftIds.value = [...selected].sort((left, right) => left - right)
}

function riskPermissionIds(risk: TokenPermissionRisk) {
  return groups.value
    .filter((group) => group.risk === risk)
    .flatMap((group) => group.permissions.map((permission) => permission.id))
}

function riskAllSelected(risk: TokenPermissionRisk) {
  const permissionIds = riskPermissionIds(risk)
  return permissionIds.length > 0
    && permissionIds.every((permissionId) => permissionDraftIds.value.includes(permissionId))
}

function toggleRisk(risk: TokenPermissionRisk) {
  const permissionIds = riskPermissionIds(risk)
  const shouldSelect = !riskAllSelected(risk)
  const selected = new Set(permissionDraftIds.value)
  permissionIds.forEach((permissionId) => {
    if (shouldSelect) {
      selected.add(permissionId)
    } else {
      selected.delete(permissionId)
    }
  })
  permissionDraftIds.value = [...selected].sort((left, right) => left - right)
}

function setScopeMode(value: boolean | string | number) {
  form.scopeMode = value ? 'FOLLOW_ACCOUNT' : 'FIXED'
  if (form.scopeMode === 'FOLLOW_ACCOUNT') {
    form.permissionIds = []
    permissionDraftIds.value = []
    permissionDialogVisible.value = false
  }
}

function openPermissionDialog() {
  if (isFollowAccount.value) {
    return
  }
  permissionDraftIds.value = [...form.permissionIds]
  groupKeyword.value = ''
  expandedGroups.value = []
  permissionDialogVisible.value = true
}

function closePermissionDialog() {
  permissionDialogVisible.value = false
}

function confirmPermissionDialog() {
  if (permissionDraftIds.value.length === 0) {
    ElMessage.warning('请至少选择一项 API 权限')
    return
  }
  form.permissionIds = [...permissionDraftIds.value]
  permissionDialogVisible.value = false
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
    ElMessage.warning('请至少选择一项 API 权限')
    openPermissionDialog()
    return
  }
  const expiresAt = resolveExpiresAt()
  if (form.expiryMode === 'CUSTOM' && !expiresAt) {
    ElMessage.warning('请选择有效的过期时间')
    return
  }

  submitting.value = true
  try {
    await createPersonalAccessToken({
      name: form.name.trim(),
      description: form.description.trim() || null,
      expiresAt,
      permissionIds: form.permissionIds,
      scopeMode: form.scopeMode,
    })
    ElMessage.success('访问密钥已创建')
    await router.replace('/access-tokens')
  } finally {
    submitting.value = false
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
              <p>配置密钥可调用的 API，最终权限不会超过当前账号已有权限。</p>
            </div>
          </div>
          <div :class="['permission-summary', { 'permission-summary--follow': isFollowAccount }]">
            <span class="permission-summary__icon"><el-icon><Key /></el-icon></span>
            <div class="permission-summary__content">
              <span>当前权限范围</span>
              <strong>{{ isFollowAccount ? '自动跟随账号权限' : `已配置 ${selectedCount} 项 API 权限` }}</strong>
              <p>
                {{ isFollowAccount
                  ? '账号权限新增或回收时，此密钥权限同步变化。'
                  : selectedCount > 0
                    ? '可重新配置权限组或精确调整单项 API 权限。'
                    : '尚未配置权限，创建密钥前至少选择一项 API 权限。' }}
              </p>
            </div>
            <el-tag v-if="isFollowAccount" type="success" effect="plain">自动跟随</el-tag>
            <el-button
              v-else
              type="primary"
              plain
              :icon="Setting"
              :loading="loading"
              @click="openPermissionDialog"
            >
              配置权限
            </el-button>
          </div>

          <div class="form-actions">
            <el-button @click="goBack">取消</el-button>
            <el-button type="primary" :loading="submitting" :icon="Key" @click="handleCreate">
              创建访问密钥
            </el-button>
          </div>
        </el-form>
      </section>
    </div>

    <el-dialog
      v-model="permissionDialogVisible"
      class="permission-config-dialog"
      title="配置权限范围"
      width="1040px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div v-loading="loading" class="permission-dialog__content">
        <div class="permission-scope__toolbar">
          <el-input v-model="groupKeyword" :prefix-icon="Search" clearable placeholder="搜索权限组、名称或编码" />
          <span>已选择 {{ draftSelectedCount }} 项 API 权限</span>
        </div>
        <div class="permission-dialog__scroll">
            <div class="permission-dialog__hint">点击权限组标题可展开或收起，复选框用于整组选择。</div>
            <div v-for="risk in (['LOW', 'HIGH'] as TokenPermissionRisk[])" :key="risk" class="risk-section">
              <header :class="['risk-section__header', `risk-section__header--${risk.toLowerCase()}`]">
                <div>
                  <strong>{{ riskMeta[risk].title }}</strong>
                  <span>{{ riskMeta[risk].description }}</span>
                </div>
                <div class="risk-section__actions">
                  <el-button
                    size="small"
                    :icon="riskAllSelected(risk) ? Close : Check"
                    :disabled="riskPermissionIds(risk).length === 0"
                    @click="toggleRisk(risk)"
                  >
                    {{ riskAllSelected(risk) ? '取消全选' : '全选' }}
                  </el-button>
                  <el-tag :type="risk === 'HIGH' ? 'danger' : 'success'" effect="plain">
                    {{ groupsByRisk[risk].length }} 组
                  </el-tag>
                </div>
              </header>
              <el-collapse v-model="expandedGroups" class="permission-groups">
                <el-collapse-item
                  v-for="group in groupsByRisk[risk]"
                  :key="group.id"
                  :name="group.id"
                >
                  <template #title>
                    <div class="group-title">
                      <el-checkbox
                        :model-value="groupChecked(group)"
                        :indeterminate="groupIndeterminate(group)"
                        @click.stop
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
                      <el-checkbox
                        :model-value="permissionChecked(permission.id)"
                        :aria-label="`选择权限：${permission.permissionName}`"
                        @change="(value) => togglePermission(permission.id, value)"
                      />
                      <div class="permission-member__content">
                        <span>{{ permission.permissionName }}</span>
                        <code>{{ permission.permissionCode }}</code>
                      </div>
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
        </div>
      <template #footer>
        <div class="permission-dialog__footer">
          <span>已选择 {{ draftSelectedCount }} 项 API 权限</span>
          <div>
            <el-button @click="closePermissionDialog">取消</el-button>
            <el-button type="primary" @click="confirmPermissionDialog">确认配置</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<style scoped lang="scss">
.token-create-layout {
  display: block;
}

.token-create-main {
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

.section-heading h2 {
  margin: 0;
  color: #1f2d2a;
  font-size: 17px;
  font-weight: 650;
}

.section-heading p {
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

.permission-summary {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  margin-top: 18px;
  border: 1px solid #dbe5e2;
  border-radius: 8px;
  padding: 16px 18px;
  background: #f8fbfa;
}

.permission-summary--follow {
  border-color: #c7dfd9;
  background: #f2f8f6;
}

.permission-summary__icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border: 1px solid #c9ddd8;
  border-radius: 8px;
  color: #15736b;
  background: #edf6f4;
  font-size: 19px;
}

.permission-summary__content {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.permission-summary__content > span {
  color: #7b8985;
  font-size: 11px;
}

.permission-summary__content strong {
  color: #253733;
  font-size: 14px;
  font-weight: 650;
}

.permission-summary__content p {
  margin: 0;
  color: #71807c;
  font-size: 12px;
  line-height: 1.5;
}

.permission-dialog__content {
  overflow: hidden;
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

.permission-dialog__scroll {
  max-height: 60vh;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.permission-dialog__hint {
  padding: 10px 16px 0;
  color: #7b8985;
  font-size: 12px;
}

.permission-dialog__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.permission-dialog__footer > span {
  color: #687773;
  font-size: 12px;
}

.permission-dialog__footer > div {
  display: flex;
  gap: 10px;
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

.risk-section__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.risk-section__actions .el-button {
  min-width: 76px;
}

.permission-groups {
  border-top: 1px solid #e5ecea;
}

.group-title {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  line-height: 1.4;
}

.group-title > span {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.group-title strong {
  color: #2c3d39;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
}

.group-title small {
  color: #899590;
  font-size: 11px;
  line-height: 1.3;
}

.permission-members {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 18px;
  padding: 2px 14px 14px 42px;
}

.permission-member {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  min-width: 0;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 6px 0;
  border-bottom: 1px solid #edf1f0;
}

.permission-member__content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.permission-member__content span,
.permission-member__content code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-member__content span {
  color: #4b5b56;
  font-size: 12px;
  line-height: 1.5;
}

.permission-member__content code {
  color: #8a9692;
  font-family: Consolas, monospace;
  font-size: 11px;
  line-height: 1.5;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 28px;
  padding-top: 18px;
  border-top: 1px solid #e5ecea;
}

:deep(.el-alert) {
  margin-top: 14px;
}

:deep(.el-collapse-item__header) {
  height: auto;
  min-height: 54px;
  padding: 7px 0;
  color: #3e504b;
  font-size: 13px;
  line-height: 1.4;
}

:deep(.el-collapse-item__arrow) {
  flex: 0 0 auto;
}

:deep(.el-collapse-item__wrap) {
  background: transparent;
}

:deep(.el-segmented) {
  --el-segmented-item-selected-color: #155e57;
  --el-segmented-item-selected-bg-color: #e5f1ee;
}

@media (prefers-reduced-motion: reduce) {
  .token-create-main {
    transition: none;
  }
}
</style>
