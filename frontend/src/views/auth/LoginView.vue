<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { forgotPassword } from '@/api/modules/auth'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const forgotPasswordLoading = ref(false)
const loginFormRef = ref()
const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleSubmit() {
  if (!loginFormRef.value) {
    return
  }

  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true

  try {
    await authStore.signIn({
      username: form.username,
      password: form.password,
    })

    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } finally {
    loading.value = false
  }
}

async function handleForgotPassword() {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入需要重置密码的工号，系统会将新密码发送到绑定内网邮箱。',
      '忘记密码',
      {
        confirmButtonText: '发送邮件',
        cancelButtonText: '取消',
        inputPlaceholder: '请输入工号',
        inputValidator: (inputValue) => (inputValue && inputValue.trim() ? true : '请输入工号'),
      },
    )

    forgotPasswordLoading.value = true
    const notice = await forgotPassword({
      username: value.trim(),
    })
    ElMessage.success(notice || '如账号信息有效，系统已向绑定内网邮箱发送重置邮件，请注意查收')
  } catch {
    // 用户取消时不做额外处理
  } finally {
    forgotPasswordLoading.value = false
  }
}
</script>

<template>
  <div class="login-view">
    <div class="login-view__hero">
      <img class="login-view__hero-logo" src="/login-logo.png" alt="统一身份管理平台 Logo" />
      <div class="login-view__hero-copy">
        <h1>统一身份管理平台</h1>
      </div>
      <div class="login-view__hero-illustration-wrap">
        <img class="login-view__hero-illustration" src="/login-hero.png" alt="统一身份管理平台品牌视觉" />
      </div>
    </div>

    <el-card class="login-view__card" shadow="never">
      <template #header>
        <div class="login-view__card-header">
          <strong>登录系统</strong>
        </div>
      </template>

      <el-form ref="loginFormRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="工号" prop="username">
          <el-input v-model="form.username" :prefix-icon="User" placeholder="请输入工号" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            :prefix-icon="Lock"
            placeholder="请输入密码"
            show-password
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <el-button class="login-view__submit" type="primary" :loading="loading" @click="handleSubmit">
          登录管理台
        </el-button>
        <div class="login-view__assist">
          <el-button link type="primary" :loading="forgotPasswordLoading" @click="handleForgotPassword">
            忘记密码
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.login-view {
  display: grid;
  grid-template-columns: minmax(420px, 1.2fr) minmax(360px, 420px);
  gap: 48px;
  align-items: center;
  min-height: 100vh;
  padding: 48px 72px;
  background:
    radial-gradient(circle at top left, rgba(91, 143, 249, 0.22), transparent 28%),
    radial-gradient(circle at bottom right, rgba(124, 77, 255, 0.18), transparent 30%),
    linear-gradient(135deg, #eef3ff 0%, #f7f9fd 42%, #ffffff 100%);
}

.login-view__hero {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 96px);
}

.login-view__hero-logo {
  width: 190px;
  max-width: 100%;
  object-fit: contain;
}

.login-view__hero-copy {
  margin-top: 64px;
}

.login-view__hero h1 {
  margin: 0;
  color: var(--idm-text-primary);
  font-size: 56px;
  line-height: 1.1;
}

.login-view__hero-illustration-wrap {
  display: flex;
  align-items: flex-end;
  flex: 1;
  min-height: 0;
  padding-top: 20px;
  margin-left: -72px;
  margin-bottom: -48px;
}

.login-view__hero-illustration {
  width: min(960px, 150%);
  object-fit: contain;
  object-position: left bottom;
}

.login-view__card {
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 28px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.14);
  backdrop-filter: blur(14px);
}

.login-view__card-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.login-view__submit {
  width: 100%;
  margin-top: 8px;
}

.login-view__assist {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
