<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Download, Hide, View, Edit, User, StarFilled, Postcard, Avatar, Message, OfficeBuilding } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useMutation } from '@tanstack/vue-query'
import { changeMyPassword, verifyMyPassword } from '@/api/modules/user'
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
