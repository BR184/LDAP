<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useMutation } from '@tanstack/vue-query'
import { changeMyPassword } from '@/api/modules/user'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const passwordVisible = ref(false)
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

const changePasswordMutation = useMutation({
  mutationFn: changeMyPassword,
  onSuccess: async () => {
    ElMessage.success('密码修改成功，请重新登录')
    passwordVisible.value = false
    authStore.clearSession()
    await router.replace('/login')
  },
})

function openPasswordDialog() {
  passwordVisible.value = true
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

async function handleChangePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  await changePasswordMutation.mutateAsync({
    oldPassword: passwordForm.oldPassword,
    newPassword: passwordForm.newPassword,
    confirmPassword: passwordForm.confirmPassword,
  })
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

    <el-dialog v-model="passwordVisible" title="修改密码" width="460px">
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" show-password />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="passwordVisible = false">取消</el-button>
          <el-button type="primary" :loading="changePasswordMutation.isPending.value" @click="handleChangePassword">
            保存密码
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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
