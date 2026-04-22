<script setup lang="ts">
import { computed } from 'vue'
import { Fold, Expand, SwitchButton } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { useMenuStore } from '@/stores/menu'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()
const menuStore = useMenuStore()

const activeMenu = computed(() => {
  const match = menuStore.visibleNavigation.find((item) => route.path.startsWith(item.path))
  return match?.path || '/dashboard'
})

const breadcrumbs = computed(() =>
  route.matched
    .filter((record) => Boolean(record.meta?.title) && record.path !== '/')
    .map((record) => ({
      title: String(record.meta?.title),
      path: record.path,
    })),
)

async function handleLogout() {
  try {
    await ElMessageBox.confirm('退出后将返回登录页，是否继续？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    })

    authStore.clearSession()
    await router.push('/login')
  } catch {
    // 用户取消退出时不做额外处理。
  }
}

function handleMenuSelect(index: string) {
  if (route.path === index) {
    return
  }
  router.push(index)
}
</script>

<template>
  <el-container class="admin-layout">
    <el-aside class="admin-layout__aside" :width="appStore.sidebarCollapsed ? '72px' : '240px'">
      <div class="admin-layout__brand">
        <div class="admin-layout__logo">ID</div>
        <div v-if="!appStore.sidebarCollapsed" class="admin-layout__brand-copy">
          <strong>Corp IDM</strong>
          <span>统一身份管理台</span>
        </div>
      </div>

      <el-scrollbar class="admin-layout__menu-scroll">
        <el-menu
          class="admin-layout__menu"
          :collapse="appStore.sidebarCollapsed"
          :default-active="activeMenu"
          @select="handleMenuSelect"
        >
          <el-menu-item v-for="item in menuStore.visibleNavigation" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="admin-layout__header">
        <div class="admin-layout__header-left">
          <el-button circle text @click="appStore.toggleSidebar()">
            <el-icon>
              <component :is="appStore.sidebarCollapsed ? Expand : Fold" />
            </el-icon>
          </el-button>

          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="crumb in breadcrumbs" :key="crumb.path">
              {{ crumb.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="admin-layout__header-right">
          <el-button text @click="router.push('/profile')">
            {{ authStore.displayName }}
          </el-button>
          <el-button circle text @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
      </el-header>

      <el-main class="admin-layout__main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
.admin-layout {
  min-height: 100vh;
  background: var(--idm-page-background);
}

.admin-layout__aside {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--idm-border-color);
  background: var(--idm-sidebar-background);
  transition: width 0.2s ease;
}

.admin-layout__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 64px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.admin-layout__logo {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #5b8ff9, #7c4dff);
  color: #fff;
  font-weight: 700;
}

.admin-layout__brand-copy {
  display: flex;
  flex-direction: column;
  color: #f5f7fa;
}

.admin-layout__brand-copy span {
  color: rgba(255, 255, 255, 0.64);
  font-size: 12px;
}

.admin-layout__menu-scroll {
  flex: 1;
}

.admin-layout__menu {
  border-right: none;
  background: transparent;
}

:deep(.admin-layout__menu.el-menu) {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: rgba(255, 255, 255, 0.78);
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
  --el-menu-active-color: #ffffff;
}

:deep(.admin-layout__menu .el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(91, 143, 249, 0.28), rgba(124, 77, 255, 0.18));
}

.admin-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 24px;
  border-bottom: 1px solid var(--idm-border-color);
  background: #ffffff;
}

.admin-layout__header-left,
.admin-layout__header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.admin-layout__main {
  padding: 24px;
}
</style>
