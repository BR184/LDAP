<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const loginFormRef = ref()
const form = reactive({
  username: 'admin',
  password: 'admin123456',
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
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
</script>

<template>
  <div class="login-view">
    <div class="login-view__hero">
      <div class="login-view__hero-badge">企业后台 / Vue 3 / Element Plus</div>
      <h1>统一身份平台管理台</h1>
      <p>
        面向用户、部门、角色、菜单、同步任务和 LDAP 接入控制面的统一后台骨架。
      </p>

      <ul class="login-view__feature-list">
        <li>统一认证与 JWT 登录态管理</li>
        <li>标准化列表页、树页和关系配置页</li>
        <li>与现有后端接口直接对齐，便于逐页接入</li>
      </ul>
    </div>

    <el-card class="login-view__card" shadow="never">
      <template #header>
        <div class="login-view__card-header">
          <strong>登录系统</strong>
          <span>默认使用本地管理员账号演示</span>
        </div>
      </template>

      <el-form ref="loginFormRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :prefix-icon="User" placeholder="请输入账号" />
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

.login-view__hero-badge {
  display: inline-flex;
  align-items: center;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(22, 119, 255, 0.1);
  color: var(--idm-primary);
  font-size: 13px;
  font-weight: 600;
}

.login-view__hero h1 {
  margin: 20px 0 16px;
  font-size: 42px;
  line-height: 1.1;
}

.login-view__hero p {
  max-width: 560px;
  margin: 0;
  color: var(--idm-text-secondary);
  font-size: 16px;
  line-height: 1.8;
}

.login-view__feature-list {
  display: grid;
  gap: 12px;
  padding: 0;
  margin: 32px 0 0;
  list-style: none;
}

.login-view__feature-list li {
  position: relative;
  padding-left: 18px;
  color: var(--idm-text-primary);
}

.login-view__feature-list li::before {
  content: '';
  position: absolute;
  top: 10px;
  left: 0;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--idm-primary), var(--idm-accent));
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

.login-view__card-header span {
  color: var(--idm-text-secondary);
  font-size: 13px;
}

.login-view__submit {
  width: 100%;
  margin-top: 8px;
}
</style>
