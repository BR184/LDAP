<script setup lang="ts">
import { computed } from 'vue'
import { DataBoard, Expand, Fold, SwitchButton, User } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { useMenuStore } from '@/stores/menu'
import { sidebarNavigationGroups } from '@/constants/navigation'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()
const menuStore = useMenuStore()

const activeMenu = computed(() => {
  if (route.path === '/profile') {
    return '/profile'
  }

  const match = menuStore.visibleNavigation.find((item) => route.path.startsWith(item.path))
  return match?.path || (authStore.isAdmin ? '/dashboard' : '/profile')
})

const visibleSidebarGroups = computed(() =>
  sidebarNavigationGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => menuStore.visibleNavigation.some((visible) => visible.path === item.path)),
    }))
    .filter((group) => group.items.length > 0),
)

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
        <img
          v-if="appStore.sidebarCollapsed"
          class="admin-layout__brand-icon"
          src="/CrownCAD_icon.png"
          alt="CrownCAD"
        />
        <img v-else class="admin-layout__brand-logo" src="/CrownCAD_logo.png" alt="CrownCAD" />
      </div>

      <el-scrollbar class="admin-layout__menu-scroll">
        <el-menu
          class="admin-layout__menu"
          :collapse="appStore.sidebarCollapsed"
          :default-active="activeMenu"
          @select="handleMenuSelect"
        >
          <el-menu-item v-if="authStore.isAdmin" index="/dashboard">
            <el-icon><DataBoard /></el-icon>
            <template #title>首页</template>
          </el-menu-item>

          <el-menu-item index="/profile">
            <el-icon><User /></el-icon>
            <template #title>个人中心</template>
          </el-menu-item>

          <el-sub-menu v-for="group in visibleSidebarGroups" :key="group.title" :index="group.title">
            <template #title>
              <el-icon><component :is="group.icon" /></el-icon>
              <span>{{ group.title }}</span>
            </template>

            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ item.title }}</template>
            </el-menu-item>
          </el-sub-menu>
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
          <span class="admin-layout__username">{{ authStore.displayName }}</span>
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
  justify-content: center;
  height: 64px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.admin-layout__brand-logo {
  display: block;
  width: 176px;
  max-width: 100%;
  height: auto;
}

.admin-layout__brand-icon {
  display: block;
  width: 36px;
  height: 36px;
  object-fit: contain;
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

.admin-layout__username {
  color: var(--idm-text-primary);
  font-size: 14px;
  font-weight: 500;
}

.admin-layout__main {
  padding: 24px;
}
</style>
