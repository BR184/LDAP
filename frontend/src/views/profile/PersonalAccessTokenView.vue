<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  CopyDocument,
  Delete,
  Key,
  Plus,
  Refresh,
  RefreshRight,
  View,
  Warning,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deletePersonalAccessToken,
  fetchPersonalAccessTokens,
  revealPersonalAccessToken,
  revokePersonalAccessToken,
  rotatePersonalAccessToken,
} from '@/api/modules/personal-access-token'
import type {
  PersonalAccessTokenItem,
  PersonalAccessTokenScopeMode,
  PersonalAccessTokenStatus,
} from '@/types/personal-access-token'

const router = useRouter()
const tokens = ref<PersonalAccessTokenItem[]>([])
const loading = ref(false)
const actionId = ref<number | null>(null)
const secretVisible = ref(false)
const secretValue = ref('')
const secretTitle = ref('')
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const statusMeta: Record<PersonalAccessTokenStatus, { label: string; type: 'success' | 'warning' | 'info' }> = {
  ACTIVE: { label: '有效', type: 'success' },
  EXPIRED: { label: '已过期', type: 'warning' },
  REVOKED: { label: '已撤销', type: 'info' },
}

const activeCount = computed(() => tokens.value.filter((token) => token.status === 'ACTIVE').length)
const recoverableCount = computed(() => tokens.value.filter((token) => token.secretRecoverable).length)

onMounted(loadTokens)

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

function openCreatePage() {
  router.push('/access-tokens/new')
}

async function handleReveal(token: PersonalAccessTokenItem) {
  if (token.status !== 'ACTIVE') {
    ElMessage.warning('只有有效的访问密钥可以查看')
    return
  }
  if (!token.secretRecoverable) {
    ElMessage.warning('该历史密钥无法恢复，请先执行轮换')
    return
  }
  actionId.value = token.id
  try {
    const result = await revealPersonalAccessToken(token.id)
    secretValue.value = result.secret
    secretTitle.value = token.name
    secretVisible.value = true
  } finally {
    actionId.value = null
  }
}

async function handleRotate(token: PersonalAccessTokenItem) {
  try {
    await ElMessageBox.confirm(
      `轮换“${token.name}”后，旧密钥会立即失效。新密钥生成后可反复复制。`,
      '轮换访问密钥',
      { type: 'warning', confirmButtonText: '轮换', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' },
    )
  } catch {
    return
  }
  actionId.value = token.id
  try {
    const result = await rotatePersonalAccessToken(token.id)
    secretValue.value = result.secret
    secretTitle.value = `${result.token.name}（已轮换）`
    secretVisible.value = true
    ElMessage.success('访问密钥已轮换')
    await loadTokens()
  } finally {
    actionId.value = null
  }
}

async function handleRevoke(token: PersonalAccessTokenItem) {
  try {
    await ElMessageBox.confirm(`撤销“${token.name}”后，使用该密钥的调用将立即失败。`, '撤销访问密钥', {
      type: 'warning',
      confirmButtonText: '撤销',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return
  }
  actionId.value = token.id
  try {
    await revokePersonalAccessToken(token.id)
    ElMessage.success('访问密钥已撤销')
    await loadTokens()
  } finally {
    actionId.value = null
  }
}

async function handleDelete(token: PersonalAccessTokenItem) {
  try {
    await ElMessageBox.confirm(
      `删除“${token.name}”会移除密钥记录及其权限关系，且无法撤回。`,
      '删除访问密钥',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' },
    )
  } catch {
    return
  }
  actionId.value = token.id
  try {
    await deletePersonalAccessToken(token.id)
    ElMessage.success('访问密钥已删除')
    await loadTokens()
  } finally {
    actionId.value = null
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

function closeSecret() {
  secretVisible.value = false
  secretValue.value = ''
  secretTitle.value = ''
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

function scopeLabel(scopeMode: PersonalAccessTokenScopeMode | null) {
  return scopeMode === 'FOLLOW_ACCOUNT' ? '自动跟随账号' : '固定权限范围'
}

function permissionSummary(token: PersonalAccessTokenItem) {
  if (token.scopeMode === 'FOLLOW_ACCOUNT') {
    return '账号当前全部 API 权限'
  }
  if (token.permissions.length === 0) {
    return '无固定权限'
  }
  const first = token.permissions.slice(0, 2).map((permission) => permission.permissionName).join('、')
  return token.permissions.length > 2 ? `${first} 等 ${token.permissions.length} 项` : first
}
</script>

<template>
  <PageContainer title="访问密钥" description="管理个人 API 凭证，查看、复制、轮换、撤销或删除均会留下审计记录。">
    <template #extra>
      <el-button type="primary" :icon="Plus" @click="openCreatePage">创建访问密钥</el-button>
    </template>

    <section class="token-overview">
      <div class="token-overview__identity">
        <span class="token-overview__icon"><el-icon><Key /></el-icon></span>
        <div>
          <span class="token-overview__eyebrow">PERSONAL ACCESS TOKENS</span>
          <strong>个人 API 凭证</strong>
        </div>
      </div>
      <div class="token-overview__metrics">
        <div><span>当前页有效</span><strong>{{ activeCount }}</strong></div>
        <div><span>密钥总数</span><strong>{{ pagination.total }}</strong></div>
        <div><span>可重复查看</span><strong>{{ recoverableCount }}</strong></div>
      </div>
    </section>

    <section class="token-surface">
      <header class="token-surface__toolbar">
        <div><h2>密钥清单</h2><span>{{ pagination.total }} 项</span></div>
        <el-tooltip content="刷新清单" placement="top">
          <el-button :icon="Refresh" circle text :loading="loading" @click="loadTokens" />
        </el-tooltip>
      </header>

      <el-table v-loading="loading" :data="tokens" class="token-table" empty-text="暂无访问密钥">
        <el-table-column label="名称" min-width="230">
          <template #default="{ row }: { row: PersonalAccessTokenItem }">
            <div class="token-name-cell">
              <strong>{{ row.name }}</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="106">
          <template #default="{ row }: { row: PersonalAccessTokenItem }">
            <el-tag :type="statusMeta[row.status].type" effect="light" round>{{ statusMeta[row.status].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限模式" min-width="220">
          <template #default="{ row }: { row: PersonalAccessTokenItem }">
            <div class="scope-cell">
              <span>{{ scopeLabel(row.scopeMode) }}</span>
              <small>{{ permissionSummary(row) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="有效期" min-width="168">
          <template #default="{ row }: { row: PersonalAccessTokenItem }">{{ formatExpiry(row.expiresAt) }}</template>
        </el-table-column>
        <el-table-column label="最近使用" min-width="188">
          <template #default="{ row }: { row: PersonalAccessTokenItem }">
            <div class="last-used-cell">
              <span>{{ formatDateTime(row.lastUsedAt) }}</span>
              <small v-if="row.lastUsedIp">{{ row.lastUsedIp }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right" align="right">
          <template #default="{ row }: { row: PersonalAccessTokenItem }">
            <div class="token-actions">
              <el-tooltip content="查看并复制完整密钥" placement="top">
                <el-button
                  v-if="row.status === 'ACTIVE' && row.secretRecoverable"
                  text
                  :icon="View"
                  :loading="actionId === row.id"
                  @click="handleReveal(row)"
                />
              </el-tooltip>
              <el-tooltip content="历史密钥不可恢复，轮换生成新密钥" placement="top">
                <el-button
                  v-if="row.status === 'ACTIVE' && !row.secretRecoverable"
                  text
                  type="warning"
                  :icon="Warning"
                  :loading="actionId === row.id"
                  @click="handleRotate(row)"
                />
              </el-tooltip>
              <el-tooltip content="轮换密钥" placement="top">
                <el-button
                  v-if="row.status === 'ACTIVE' && row.secretRecoverable"
                  text
                  :icon="RefreshRight"
                  :loading="actionId === row.id"
                  @click="handleRotate(row)"
                />
              </el-tooltip>
              <el-button v-if="row.status === 'ACTIVE'" text type="warning" @click="handleRevoke(row)">撤销</el-button>
              <el-tooltip content="永久删除密钥记录" placement="top">
                <el-button text type="danger" :icon="Delete" :loading="actionId === row.id" @click="handleDelete(row)" />
              </el-tooltip>
            </div>
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

    <el-dialog v-model="secretVisible" title="完整访问密钥" width="640px" :close-on-click-modal="false" @closed="closeSecret">
      <div class="secret-result">
        <span class="secret-result__success"><el-icon><Key /></el-icon></span>
        <div><strong>{{ secretTitle }}</strong><p>当前会话内可反复复制，不会出现在密钥清单或日志中。</p></div>
      </div>
      <div class="secret-value">
        <code>{{ secretValue }}</code>
        <el-tooltip content="复制完整密钥" placement="top">
          <el-button :icon="CopyDocument" circle @click="copySecret" />
        </el-tooltip>
      </div>
      <template #footer><el-button type="primary" @click="closeSecret">关闭</el-button></template>
    </el-dialog>
  </PageContainer>
</template>

<style scoped lang="scss">
.token-overview,
.token-surface {
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
  border: 1px solid rgba(109, 130, 126, 0.2);
  border-radius: 8px;
  background: rgba(250, 252, 251, 0.78);
  box-shadow: 0 18px 45px rgba(30, 53, 50, 0.08);
  backdrop-filter: blur(18px) saturate(1.2);
}

.token-overview__identity { display: flex; align-items: center; gap: 16px; }
.token-overview__identity strong { display: block; margin-top: 4px; color: #172522; font-size: 21px; font-weight: 650; }
.token-overview__icon { display: grid; width: 48px; height: 48px; place-items: center; border: 1px solid rgba(21, 115, 107, 0.22); border-radius: 8px; color: #15736b; background: rgba(223, 240, 236, 0.7); font-size: 23px; }
.token-overview__eyebrow { color: #6f7f7c; font-family: Consolas, monospace; font-size: 11px; }
.token-overview__metrics { display: grid; grid-template-columns: repeat(3, minmax(96px, 1fr)); gap: 1px; overflow: hidden; border: 1px solid rgba(109, 130, 126, 0.18); border-radius: 8px; background: rgba(109, 130, 126, 0.16); }
.token-overview__metrics div { display: flex; min-width: 104px; padding: 14px 16px; flex-direction: column; gap: 6px; background: rgba(255, 255, 255, 0.8); }
.token-overview__metrics span { color: #788683; font-size: 12px; }
.token-overview__metrics strong { color: #172522; font-size: 22px; font-weight: 650; font-variant-numeric: tabular-nums; }

.token-surface { overflow: hidden; border: 1px solid rgba(109, 130, 126, 0.18); border-radius: 8px; background: rgba(255, 255, 255, 0.84); box-shadow: 0 18px 42px rgba(30, 53, 50, 0.07); backdrop-filter: blur(16px); }
.token-surface__toolbar { display: flex; min-height: 64px; align-items: center; justify-content: space-between; padding: 0 20px; border-bottom: 1px solid rgba(109, 130, 126, 0.14); }
.token-surface__toolbar div { display: flex; align-items: baseline; gap: 10px; }
.token-surface__toolbar h2 { margin: 0; color: #1d2c29; font-size: 16px; font-weight: 650; }
.token-surface__toolbar span { color: #8a9693; font-size: 12px; }
.token-table { --el-table-border-color: rgba(109, 130, 126, 0.12); --el-table-header-bg-color: rgba(242, 246, 245, 0.82); --el-table-row-hover-bg-color: rgba(237, 246, 244, 0.72); width: 100%; }
.token-table :deep(th.el-table__cell) { height: 46px; color: #65736f; font-size: 12px; font-weight: 600; }
.token-table :deep(td.el-table__cell) { padding: 14px 0; }
.token-name-cell, .scope-cell, .last-used-cell { display: flex; min-width: 0; flex-direction: column; gap: 5px; }
.token-name-cell strong { overflow: hidden; color: #1f2d2a; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.scope-cell small, .last-used-cell small { overflow: hidden; color: #7b8985; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.scope-cell > span { color: #315b56; font-size: 13px; font-weight: 600; }
.token-actions { display: flex; align-items: center; justify-content: flex-end; gap: 2px; }
.token-surface__footer { display: flex; justify-content: flex-end; padding: 14px 20px; border-top: 1px solid rgba(109, 130, 126, 0.12); }
.secret-result { display: flex; align-items: center; gap: 14px; margin-bottom: 18px; }
.secret-result strong { color: #21312d; font-size: 16px; }
.secret-result p { margin: 5px 0 0; color: #73817d; font-size: 13px; }
.secret-result__success { display: grid; width: 40px; height: 40px; flex: 0 0 auto; place-items: center; border-radius: 50%; color: #fff; background: #18856f; font-size: 20px; }
.secret-value { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 12px; align-items: center; padding: 14px; border: 1px solid #c9d9d5; border-radius: 8px; background: #f4f8f7; }
.secret-value code { overflow-wrap: anywhere; color: #163d37; font-family: Consolas, monospace; font-size: 13px; line-height: 1.6; user-select: all; }

@media (prefers-reduced-motion: reduce) { .token-overview, .token-surface { transition: none; } }
</style>
