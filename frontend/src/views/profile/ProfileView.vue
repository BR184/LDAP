<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Download, Hide, View } from '@element-plus/icons-vue'
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
    <el-card class="idm-card" shadow="never">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="数据库ID">{{ authStore.currentUser?.id || '--' }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ authStore.currentUser?.userId || '--' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ authStore.currentUser?.realName || '--' }}</el-descriptions-item>
        <el-descriptions-item label="工作邮箱">{{ authStore.currentUser?.email || '--' }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ authStore.currentUser?.deptCode || '--' }}</el-descriptions-item>
      </el-descriptions>

      <div class="profile-actions">
        <el-button type="primary" @click="openPasswordDialog">修改密码</el-button>
      </div>
    </el-card>

    <el-dialog v-model="passwordVisible" title="修改密码" width="520px">
      <el-alert
        title="此密码极为重要，请将密码记录在安全的地方，牢记此密码！"
        type="warning"
        show-icon
        :closable="false"
        class="password-tip"
      />

      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" :type="showOldPassword ? 'text' : 'password'">
            <template #suffix>
              <el-button text :icon="showOldPassword ? Hide : View" @click="showOldPassword = !showOldPassword" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" :type="showNewPassword ? 'text' : 'password'">
            <template #suffix>
              <el-button text :icon="showNewPassword ? Hide : View" @click="showNewPassword = !showNewPassword" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" :type="showConfirmPassword ? 'text' : 'password'">
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

    <el-dialog v-model="passwordConfirmVisible" title="修改密码确认" width="460px" append-to-body>
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
.profile-actions {
  margin-top: 16px;
}

.password-tip {
  margin-bottom: 16px;
}

.dialog-footer,
.confirm-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
