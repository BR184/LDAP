<script setup lang="ts">
import { computed } from 'vue'
import { Expand, Fold, Key, SwitchButton, User } from '@element-plus/icons-vue'
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
  if (route.path === '/access-tokens') {
    return '/access-tokens'
  }

  const match = menuStore.visibleNavigation.find((item) => route.path.startsWith(item.path))
  return match?.path || '/profile'
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
    <!-- 侧边栏 -->
    <el-aside class="admin-layout__aside" :width="appStore.sidebarCollapsed ? '64px' : '240px'">
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
          <el-menu-item index="/profile" class="admin-menu-item">
            <el-icon><User /></el-icon>
            <template #title>个人中心</template>
          </el-menu-item>

          <el-menu-item index="/access-tokens" class="admin-menu-item">
            <el-icon><Key /></el-icon>
            <template #title>访问密钥</template>
          </el-menu-item>

          <el-sub-menu v-for="group in visibleSidebarGroups" :key="group.title" :index="group.title">
            <template #title>
              <el-icon><component :is="group.icon" /></el-icon>
              <span>{{ group.title }}</span>
            </template>

            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path" class="admin-menu-item">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ item.title }}</template>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <!-- 主容器 -->
    <el-container class="admin-layout__main-container">
      <!-- 顶部导航 -->
      <el-header class="admin-layout__header">
        <div class="admin-layout__header-left">
          <el-button class="toggle-btn" circle text @click="appStore.toggleSidebar()">
            <el-icon size="20">
              <component :is="appStore.sidebarCollapsed ? Expand : Fold" />
            </el-icon>
          </el-button>

          <el-breadcrumb separator="/" class="admin-layout__breadcrumb">
            <el-breadcrumb-item v-for="crumb in breadcrumbs" :key="crumb.path">
              {{ crumb.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="admin-layout__header-right">
          <div class="user-info">
            <span class="user-name">{{ authStore.displayName }}</span>
          </div>
          <el-button class="logout-btn" circle text @click="handleLogout">
            <el-icon size="20"><SwitchButton /></el-icon>
          </el-button>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="admin-layout__main">
        <div class="page-background-decoration"></div>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
.admin-layout {
  min-height: 100vh;
  background: var(--idm-page-background-solid);
}

.admin-layout__aside {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--idm-border-color-light);
  background: var(--idm-sidebar-background);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.04);
}

.admin-layout__brand {
  display: flex;
  align-items: center;
  justify-content: center;
  height: var(--idm-header-height);
  padding: 0 var(--idm-padding-md);
  border-bottom: 1px solid var(--idm-border-color-lighter);
}

.admin-layout__brand-logo {
  display: block;
  width: 160px;
  max-width: 100%;
  height: auto;
}

.admin-layout__brand-icon {
  display: block;
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.admin-layout__menu-scroll {
  flex: 1;
  padding: var(--idm-padding-sm) 0;
}

.admin-layout__menu {
  border-right: none;
  background: transparent;
}

:deep(.admin-layout__menu.el-menu) {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--idm-text-regular);
  --el-menu-hover-bg-color: var(--idm-sidebar-item-hover);
  --el-menu-active-color: var(--idm-primary);
  --el-menu-item-height: 48px;
  padding: 0 var(--idm-padding-xs);
}

:deep(.admin-layout__menu .el-menu-item),
:deep(.admin-layout__menu .el-sub-menu__title) {
  border-radius: var(--idm-radius-base);
  margin: 2px 0;
  transition: all 0.3s ease;
  font-weight: 500;
}

:deep(.admin-layout__menu .el-menu-item.is-active) {
  background: var(--idm-sidebar-item-active);
  color: var(--idm-primary);
  font-weight: 600;
}

:deep(.admin-layout__menu .el-menu-item:hover),
:deep(.admin-layout__menu .el-sub-menu__title:hover) {
  background: var(--idm-sidebar-item-hover);
}

:deep(.admin-layout__menu .el-sub-menu .el-menu-item) {
  padding-left: 48px !important;
}

.admin-layout__main-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.admin-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--idm-padding-md);
  padding: 0 var(--idm-padding-lg);
  height: var(--idm-header-height);
  border-bottom: 1px solid var(--idm-border-color-light);
  background: var(--idm-header-background);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  z-index: 100;
}

.admin-layout__header-left,
.admin-layout__header-right {
  display: flex;
  align-items: center;
  gap: var(--idm-padding-md);
}

.admin-layout__breadcrumb {
  font-size: 14px;

  :deep(.el-breadcrumb__item) {
    font-weight: 500;
  }

  :deep(.el-breadcrumb__inner) {
    color: var(--idm-text-regular);
  }

  :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
    color: var(--idm-text-primary);
    font-weight: 600;
  }
}

.toggle-btn,
.logout-btn {
  width: 40px;
  height: 40px;
  color: var(--idm-text-regular);
  transition: all 0.3s ease;

  &:hover {
    color: var(--idm-primary);
    background: var(--idm-primary-lighter);
  }
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--idm-padding-xs);
}

.user-name {
  color: var(--idm-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.admin-layout__main {
  flex: 1;
  padding: var(--idm-padding-lg);
  overflow-y: auto;
  position: relative;
}

// 背景装饰
.page-background-decoration {
  position: fixed;
  bottom: 0;
  right: 0;
  width: 60%;
  height: 50%;
  pointer-events: none;
  z-index: 0;
  background: radial-gradient(ellipse at bottom right, rgba(64, 158, 255, 0.08) 0%, transparent 60%);

  &::before {
    content: '';
    position: absolute;
    bottom: -10%;
    right: -5%;
    width: 500px;
    height: 500px;
    background: radial-gradient(circle, rgba(64, 158, 255, 0.15) 0%, transparent 70%);
    border-radius: 50%;
    filter: blur(60px);
  }

  &::after {
    content: '';
    position: absolute;
    bottom: 10%;
    right: 20%;
    width: 300px;
    height: 300px;
    background: radial-gradient(circle, rgba(124, 77, 255, 0.1) 0%, transparent 70%);
    border-radius: 50%;
    filter: blur(50px);
  }
}

// 响应式调整
@media (max-width: 1440px) {
  .admin-layout__main {
    padding: var(--idm-padding-md);
  }
}
</style>
