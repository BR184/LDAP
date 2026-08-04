<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Download, Hide, View, Edit, User, StarFilled, Postcard, Avatar, Message, OfficeBuilding, Collection, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { changeMyPassword, verifyMyPassword } from '@/api/modules/user'
import { fetchPersonalRoleContext } from '@/api/modules/role-group'
import { useAuthStore } from '@/stores/auth'

declare global {
  interface Window {
    showSaveFilePicker?: (options?: {
      suggestedName?: string
      types?: Array<{
        description?: string
        accept: Record<string, string[]>
      }>
    }) => Promise<{
      createWritable: () => Promise<{
        write: (data: Blob) => Promise<void>
        close: () => Promise<void>
      }>
    }>
  }
}

const authStore = useAuthStore()
const roleContextQuery = useQuery({ queryKey: ['profile', 'role-context'], queryFn: fetchPersonalRoleContext })

const passwordVisible = ref(false)
const passwordConfirmVisible = ref(false)
const showOldPassword = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)
const passwordVerificationToken = ref('')
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordRules: FormRules<typeof passwordForm> = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码长度不能少于 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

const currentUser = computed(() => authStore.currentUser)

watch(
  () => [passwordForm.oldPassword, passwordForm.newPassword, passwordForm.confirmPassword],
  () => {
    passwordVerificationToken.value = ''
  },
)

const changePasswordMutation = useMutation({
  mutationFn: changeMyPassword,
  onSuccess: async () => {
    ElMessage.success('密码修改成功，请重新登录')
    passwordConfirmVisible.value = false
    passwordVisible.value = false
    authStore.clearSession()
  },
})

function openPasswordDialog() {
  passwordVisible.value = true
  passwordConfirmVisible.value = false
  showOldPassword.value = false
  showNewPassword.value = false
  showConfirmPassword.value = false
  passwordVerificationToken.value = ''
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

async function handleChangePassword() {
  if (!(await ensurePasswordFormAndOldPasswordVerified())) {
    return
  }

  passwordConfirmVisible.value = true
}

async function confirmChangePassword() {
  await changePasswordMutation.mutateAsync({
    verificationToken: passwordVerificationToken.value,
    newPassword: passwordForm.newPassword,
    confirmPassword: passwordForm.confirmPassword,
  })
}

async function ensurePasswordFormAndOldPasswordVerified() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return false
  }

  const result = await verifyMyPassword({
    oldPassword: passwordForm.oldPassword,
  })
  passwordVerificationToken.value = result.verificationToken
  ElMessage.success('旧密码验证通过')
  return true
}

async function downloadCredentialFile() {
  if (!(await ensurePasswordFormAndOldPasswordVerified())) {
    return
  }

  const user = currentUser.value
  const fileContent = [
    'LDAP统一账号凭据',
    `时间: ${new Date().toLocaleString('zh-CN')}`,
    `用户ID: ${user?.userId || '--'}`,
    `姓名: ${user?.realName || '--'}`,
    `邮箱: ${user?.email || '--'}`,
    `新密码: ${passwordForm.newPassword || '--'}`,
    '',
    '此密码极为重要，请将密码记录在安全的地方，牢记此密码！',
  ].join('\n')

  const suggestedName = `LDAP统一账号-凭据-${user?.userId || 'user'}-${Date.now()}.txt`
  const blob = new Blob([fileContent], { type: 'text/plain;charset=utf-8' })

  const savePicker = window.showSaveFilePicker
  if (typeof savePicker === 'function') {
    const fileHandle = await savePicker.call(window, {
      suggestedName,
      types: [
        {
          description: '文本文件',
          accept: {
            'text/plain': ['.txt'],
          },
        },
      ],
    })
    const writable = await fileHandle.createWritable()
    await writable.write(blob)
    await writable.close()
    ElMessage.success('账号凭据已保存')
    return
  }

  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = suggestedName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <PageContainer title="个人中心" description="查看当前登录用户信息，并维护个人密码等基础账号设置。">
    <div class="profile-container">
      <!-- 用户信息卡片 -->
      <el-card class="profile-card" shadow="never">
        <div class="profile-header">
          <div class="profile-avatar">
            <el-icon :size="48" color="#409eff"><User /></el-icon>
          </div>
          <div class="profile-title">
            <h2 class="profile-name">
              {{ authStore.currentUser?.realName || '--' }}
              <el-tag v-if="authStore.isAdmin" type="primary" size="small" effect="plain">
                <el-icon><StarFilled /></el-icon>
                系统管理员
              </el-tag>
            </h2>
            <p class="profile-subtitle">{{ authStore.currentUser?.email || '--' }}</p>
          </div>
          <div class="profile-actions-header">
            <el-button type="primary" :icon="Edit" @click="openPasswordDialog">修改密码</el-button>
          </div>
        </div>

        <el-divider />

        <!-- 用户详细信息 - 横向布局 -->
        <div class="profile-details">
          <div class="detail-item">
            <el-icon class="detail-icon"><User /></el-icon>
            <div class="detail-content">
              <span class="detail-label">用户ID</span>
              <span class="detail-value">{{ authStore.currentUser?.userId || '--' }}</span>
            </div>
          </div>

          <div class="detail-item">
            <el-icon class="detail-icon"><Avatar /></el-icon>
            <div class="detail-content">
              <span class="detail-label">姓名</span>
              <span class="detail-value">{{ authStore.currentUser?.realName || '--' }}</span>
            </div>
          </div>

          <div class="detail-item">
            <el-icon class="detail-icon"><Postcard /></el-icon>
            <div class="detail-content">
              <span class="detail-label">工号</span>
              <span class="detail-value">{{ authStore.currentUser?.employeeNo || '--' }}</span>
            </div>
          </div>

          <div class="detail-item">
            <el-icon class="detail-icon"><Postcard /></el-icon>
            <div class="detail-content">
              <span class="detail-label">职务</span>
              <span class="detail-value">{{ authStore.currentUser?.jobTitle || '--' }}</span>
            </div>
          </div>

          <div class="detail-item">
            <el-icon class="detail-icon"><OfficeBuilding /></el-icon>
            <div class="detail-content">
              <span class="detail-label">部门</span>
              <span class="detail-value">{{ authStore.currentUser?.departmentPath || '--' }}</span>
            </div>
          </div>

          <div class="detail-item">
            <el-icon class="detail-icon"><Message /></el-icon>
            <div class="detail-content">
              <span class="detail-label">内网邮箱</span>
              <span class="detail-value">{{ authStore.currentUser?.intranetEmail || '--' }}</span>
            </div>
          </div>
        </div>
      </el-card>

      <section class="profile-access">
        <div class="access-panel">
          <div class="access-panel__header">
            <div class="access-panel__icon"><el-icon><Lock /></el-icon></div>
            <div><h3>我的角色</h3><span>{{ roleContextQuery.data.value?.roles.length || 0 }}</span></div>
          </div>
          <el-skeleton v-if="roleContextQuery.isLoading.value" :rows="3" animated />
          <el-empty v-else-if="!roleContextQuery.data.value?.roles.length" description="暂无角色" :image-size="64" />
          <div v-else class="access-list">
            <div v-for="role in roleContextQuery.data.value.roles" :key="role.id" class="access-list__item">
              <div><strong>{{ role.roleName }}</strong><span>{{ role.roleCode }}</span></div>
              <el-tag effect="plain">{{ role.roleGroupName || (role.roleScope === 'GLOBAL' ? '全局' : '系统级') }}</el-tag>
            </div>
          </div>
        </div>

        <div class="access-panel">
          <div class="access-panel__header">
            <div class="access-panel__icon is-group"><el-icon><Collection /></el-icon></div>
            <div><h3>参与的角色组</h3><span>{{ roleContextQuery.data.value?.roleGroups.length || 0 }}</span></div>
          </div>
          <el-skeleton v-if="roleContextQuery.isLoading.value" :rows="3" animated />
          <el-empty v-else-if="!roleContextQuery.data.value?.roleGroups.length" description="暂无参与的角色组" :image-size="64" />
          <div v-else class="access-list">
            <div v-for="group in roleContextQuery.data.value.roleGroups" :key="group.id" class="access-list__item">
              <div><strong>{{ group.groupName }}</strong></div>
              <el-tag effect="plain">{{ group.memberRole === 'OWNER' ? '所有者' : '协管员' }}</el-tag>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="passwordVisible" title="修改密码" width="520px" :close-on-click-modal="false">
      <el-alert
        title="此密码极为重要，请将密码记录在安全的地方，牢记此密码！"
        type="warning"
        show-icon
        :closable="false"
        class="password-tip"
      />

      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" :type="showOldPassword ? 'text' : 'password'" size="large">
            <template #suffix>
              <el-button text :icon="showOldPassword ? Hide : View" @click="showOldPassword = !showOldPassword" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" :type="showNewPassword ? 'text' : 'password'" size="large">
            <template #suffix>
              <el-button text :icon="showNewPassword ? Hide : View" @click="showNewPassword = !showNewPassword" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" :type="showConfirmPassword ? 'text' : 'password'" size="large">
            <template #suffix>
              <el-button text :icon="showConfirmPassword ? Hide : View" @click="showConfirmPassword = !showConfirmPassword" />
            </template>
          </el-input>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="passwordVisible = false">取消</el-button>
          <el-button :icon="Download" @click="downloadCredentialFile">下载凭据</el-button>
          <el-button type="primary" :loading="changePasswordMutation.isPending.value" @click="handleChangePassword">
            保存密码
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 修改密码确认对话框 -->
    <el-dialog v-model="passwordConfirmVisible" title="修改密码确认" width="460px" append-to-body :close-on-click-modal="false">
      <el-alert
        title="此密码极为重要，请将密码记录在安全的地方，牢记此密码！"
        type="warning"
        show-icon
        :closable="false"
      />

      <template #footer>
        <div class="confirm-footer">
          <el-button @click="passwordConfirmVisible = false">取消</el-button>
          <el-button :icon="Download" @click="downloadCredentialFile">下载凭据</el-button>
          <el-button type="primary" :loading="changePasswordMutation.isPending.value" @click="confirmChangePassword">
            我已记住
          </el-button>
        </div>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<style scoped lang="scss">
.profile-container {
  /* 移除 max-width 限制，让卡片尽量铺满屏幕 */
  width: 100%;
}

.profile-card {
  border-radius: var(--idm-radius-xl);
  box-shadow: var(--idm-shadow-card);
  border: 1px solid var(--idm-border-color-lighter);

  :deep(.el-card__body) {
    padding: var(--idm-padding-lg);
  }
}

.profile-access {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(360px, 0.75fr);
  gap: 18px;
  margin-top: 18px;
}

.access-panel {
  padding: 22px;
  border: 1px solid var(--idm-border-color-lighter);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(16px);
  box-shadow: 0 14px 34px rgba(31, 48, 38, 0.07);
}

.access-panel__header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.access-panel__header > div:last-child {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.access-panel__header h3 {
  margin: 0;
  font-size: 16px;
  letter-spacing: 0;
}

.access-panel__header span,
.access-list__item span {
  color: var(--idm-text-secondary);
  font-size: 12px;
}

.access-panel__icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  background: #e8efe9;
  color: #315b3e;
}

.access-panel__icon.is-group {
  background: #fff0df;
  color: #a85c17;
}

.access-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.access-list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 48px;
  padding: 10px 12px;
  border-top: 1px solid var(--idm-border-color-lighter);
}

.access-list__item > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: var(--idm-padding-lg);
}

.profile-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #e6f4ff 0%, #bae0ff 100%);
  flex-shrink: 0;
}

.profile-title {
  flex: 1;
}

.profile-name {
  margin: 0 0 var(--idm-padding-xs);
  font-size: 24px;
  font-weight: 600;
  color: var(--idm-text-primary);
  display: flex;
  align-items: center;
  gap: var(--idm-padding-sm);

  .el-tag {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
}

.profile-subtitle {
  margin: 0;
  font-size: 14px;
  color: var(--idm-text-secondary);
}

.profile-actions-header {
  flex-shrink: 0;
}

.profile-details {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--idm-padding-lg);
  margin-top: var(--idm-padding-lg);
}

.detail-item {
  display: flex;
  align-items: center;
  gap: var(--idm-padding-md);
  padding: var(--idm-padding-lg);
  border-radius: var(--idm-radius-base);
  background: #ffffff;
  border: 1px solid var(--idm-border-color-lighter);
  transition: all 0.3s ease;

  &:hover {
    background: var(--idm-primary-lighter);
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  }
}

.detail-icon {
  font-size: 24px;
  color: var(--idm-text-secondary);
  flex-shrink: 0;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.detail-label {
  font-size: 13px;
  color: var(--idm-text-secondary);
  font-weight: 500;
}

.detail-value {
  font-size: 16px;
  color: var(--idm-text-primary);
  font-weight: 600;
  word-break: break-all;
}

.password-tip {
  margin-bottom: var(--idm-padding-md);
  border-radius: var(--idm-radius-base);
}

.dialog-footer,
.confirm-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--idm-padding-sm);
  flex-wrap: wrap;
}

:deep(.el-divider) {
  margin: var(--idm-padding-lg) 0;
}

// 响应式
@media (max-width: 768px) {
  .profile-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .detail-row {
    grid-template-columns: 1fr;
  }
}
</style>
