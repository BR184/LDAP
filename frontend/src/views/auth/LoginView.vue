<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Lock, QuestionFilled, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const loginFormRef = ref()
const form = reactive({
  loginId: '',
  password: '',
})

const rules = {
  loginId: [{ required: true, message: '请输入用户ID', trigger: 'blur' }],
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
      loginId: form.loginId,
      password: form.password,
    })

    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : authStore.defaultEntryPath
    await router.replace(redirect)
  } finally {
    loading.value = false
  }
}

function handleForgotPassword() {
  ElMessageBox.alert('请联系直属上级或系统管理员重置密码', '忘记密码', {
    confirmButtonText: '知道了',
    type: 'info',
  })
}
</script>

<template>
  <main class="login-view">
    <section class="login-view__brand" aria-labelledby="login-page-title">
      <div class="login-view__brand-header">
        <img class="login-view__brand-logo" src="/login-logo.png" alt="华云三维" />
      </div>

      <div class="login-view__brand-intro">
        <h1 id="login-page-title">统一身份平台</h1>
        <span aria-hidden="true" class="login-view__brand-rule"></span>
        <div class="login-view__brand-description">
          <p>欢迎来到华云三维统一身份平台</p>
        </div>
      </div>

      <div class="login-view__visual" aria-hidden="true">
        <img src="/login-identity-network-v2.png" alt="" />
      </div>
    </section>

    <section class="login-view__access" aria-label="登录入口">
      <div class="login-view__panel" aria-labelledby="login-panel-title">
        <div class="login-view__panel-header">
          <h2 id="login-panel-title">欢迎登录</h2>
          <p>账号登录</p>
        </div>

        <el-form ref="loginFormRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleSubmit">
          <el-form-item prop="loginId">
            <template #label>
              <span class="login-view__field-label">
                <span>用户ID</span>
                <el-popover
                  placement="left-start"
                  :width="720"
                  trigger="hover"
                  :show-after="120"
                  :hide-after="80"
                  :persistent="false"
                  popper-class="login-user-id-help-popper"
                >
                  <template #reference>
                    <button class="login-view__help-trigger" type="button" aria-label="如何查找我的用户ID">
                      <el-icon><QuestionFilled /></el-icon>
                    </button>
                  </template>
                  <img
                    class="login-user-id-help-image"
                    src="/login-user-id-help.jpg"
                    alt="在飞书个人名片中查找用户ID的步骤"
                  />
                </el-popover>
              </span>
            </template>
            <el-input v-model="form.loginId" :prefix-icon="User" placeholder="请输入用户ID" />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              :prefix-icon="Lock"
              placeholder="请输入密码"
              show-password
            />
          </el-form-item>

          <el-button class="login-view__submit" native-type="submit" type="primary" :loading="loading">
            登录
          </el-button>
          <div class="login-view__assist">
            <el-button link type="primary" @click="handleForgotPassword">
              忘记密码
            </el-button>
          </div>
        </el-form>
      </div>
    </section>
  </main>
</template>

<style scoped lang="scss">
:global(body:has(.login-view)) {
  min-width: 0;
  background: #f5f7fa;
}

.login-view {
  --login-ink: #142b4a;
  --login-muted: #7a8ca4;
  --login-primary: #1769df;
  --login-border: #dfe7f1;
  --login-surface: #ffffff;

  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(480px, 0.9fr);
  min-height: 100vh;
  background: #f7f9fd;
  overflow: hidden;
}

.login-view__brand {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: clamp(42px, 5vw, 80px) clamp(48px, 7vw, 128px) 0;
  overflow: hidden;
}

.login-view__brand-header,
.login-view__brand-intro {
  position: relative;
  z-index: 2;
}

.login-view__brand-header {
  display: flex;
  align-items: center;
  min-height: 54px;
}

.login-view__brand-logo {
  width: 174px;
  max-width: 100%;
  object-fit: contain;
}

.login-view__brand-intro {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-top: clamp(132px, 16vh, 188px);
}

.login-view__brand h1 {
  margin: 0 0 26px;
  color: var(--login-ink);
  font-family: 'HarmonyOS Sans SC', 'MiSans', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: clamp(42px, 3.8vw, 60px);
  font-weight: 700;
  line-height: 1.12;
  letter-spacing: 0;
}

.login-view__brand-rule {
  display: block;
  width: 48px;
  height: 3px;
  background: #f39a1d;
}

.login-view__brand-description {
  margin-top: 22px;
  color: #58708f;
  font-size: 14px;
  line-height: 1.85;
}

.login-view__brand-description p {
  margin: 0;
}

.login-view__visual {
  position: absolute;
  z-index: 1;
  right: 0;
  bottom: 0;
  left: 0;
  height: min(40vw, 440px);
  overflow: hidden;
}

.login-view__visual img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center bottom;
  mix-blend-mode: multiply;
}

.login-view__access {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  min-width: 0;
  padding: clamp(44px, 7vw, 112px) clamp(40px, 5vw, 88px) clamp(44px, 7vw, 112px) clamp(28px, 4vw, 72px);
  background: transparent;
}

.login-view__panel {
  width: min(100%, 544px);
  padding: clamp(42px, 4vw, 58px);
  border: 1px solid #e8edf4;
  border-radius: 8px;
  background: var(--login-surface);
  box-shadow: 0 24px 60px rgba(22, 52, 95, 0.1);
}

.login-view__panel-header {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 40px;
}

.login-view__panel-header h2 {
  margin: 0;
  color: var(--login-ink);
  font-family: 'HarmonyOS Sans SC', 'MiSans', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: 0;
}

.login-view__panel-header p {
  margin: 10px 0 0;
  color: var(--login-muted);
  font-size: 15px;
  line-height: 1.5;
}

.login-view :deep(.el-form-item) {
  margin-bottom: 28px;
}

.login-view :deep(.el-form-item__label) {
  height: auto;
  padding-bottom: 10px;
  color: var(--login-ink);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
}

.login-view__field-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.login-view__help-trigger {
  display: inline-grid;
  width: 20px;
  height: 20px;
  padding: 0;
  place-items: center;
  border: 0;
  border-radius: 50%;
  color: #f28c18;
  background: rgba(242, 140, 24, 0.12);
  cursor: help;
  font-size: 16px;
  transition: color 160ms ease, background-color 160ms ease, box-shadow 160ms ease;
}

.login-view__help-trigger:hover,
.login-view__help-trigger:focus-visible {
  color: #d96f05;
  background: rgba(242, 140, 24, 0.2);
  box-shadow: 0 0 0 3px rgba(242, 140, 24, 0.14);
  outline: none;
}

:global(.login-user-id-help-popper.el-popper) {
  max-width: calc(100vw - 40px);
  padding: 10px;
  border-color: rgba(20, 43, 74, 0.14);
  border-radius: 8px;
  box-shadow: 0 24px 64px rgba(20, 43, 74, 0.2);
}

:global(.login-user-id-help-image) {
  display: block;
  width: 100%;
  max-height: 78vh;
  border-radius: 5px;
  object-fit: contain;
}

.login-view :deep(.el-input__wrapper) {
  min-height: 50px;
  padding: 1px 15px;
  border-radius: 6px;
  box-shadow: inset 0 0 0 1px var(--login-border);
  transition: box-shadow 0.18s ease;
}

.login-view :deep(.el-input__wrapper:hover) {
  box-shadow: inset 0 0 0 1px #aabbd1;
}

.login-view :deep(.el-input__wrapper.is-focus) {
  box-shadow: inset 0 0 0 1px var(--login-primary);
}

.login-view :deep(.el-input__inner) {
  color: var(--login-ink);
  font-size: 15px;
}

.login-view :deep(.el-input__inner::placeholder) {
  color: #9aa8ba;
}

.login-view :deep(.el-input__prefix-inner),
.login-view :deep(.el-input__suffix-inner) {
  color: #75859a;
}

.login-view__submit {
  width: 100%;
  min-height: 50px;
  margin-top: 8px;
  border: 0;
  border-radius: 6px;
  background: var(--login-primary);
  box-shadow: 0 8px 18px rgba(23, 104, 214, 0.22);
  font-size: 15px;
  font-weight: 600;
}

.login-view__submit:hover,
.login-view__submit:focus-visible {
  background: #1159bd;
}

.login-view__assist {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.login-view__assist :deep(.el-button) {
  min-height: 24px;
  padding: 0;
  color: var(--login-primary);
  font-size: 14px;
}

@media (max-width: 960px) {
  .login-view {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    min-height: 100vh;
  }

  .login-view__brand {
    flex: 0 0 auto;
    min-height: 0;
    padding: 32px 40px 8px;
    overflow: visible;
    background: transparent;
  }

  .login-view__brand-intro,
  .login-view__visual {
    display: none;
  }

  .login-view__brand-logo {
    width: 156px;
  }

  .login-view__access {
    flex: 1 1 auto;
    justify-content: center;
    padding: 32px 40px 56px;
  }
}

@media (max-width: 520px) {
  .login-view__brand {
    padding: 24px 24px 0;
  }

  .login-view__brand-logo {
    width: 144px;
  }

  .login-view__access {
    display: block;
    padding: 24px 16px 40px;
  }

  .login-view__panel {
    width: 100%;
    padding: 34px 24px;
  }

  .login-view__panel-header {
    margin-bottom: 32px;
  }

  .login-view__panel-header h2 {
    font-size: 28px;
  }
}
</style>
