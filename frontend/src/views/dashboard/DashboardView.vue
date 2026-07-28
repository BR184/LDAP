<script setup lang="ts">
import { useRouter } from 'vue-router'
import { flatNavigationItems } from '@/constants/navigation'

const router = useRouter()
</script>

<template>
  <PageContainer title="首页">
    <el-card class="dashboard-card" shadow="never">
      <template #header>
        <div class="dashboard-section__header">
          <strong>快速入口</strong>
          <span class="idm-muted">快速访问常用功能模块</span>
        </div>
      </template>

      <div class="dashboard-shortcuts">
        <button
          v-for="item in flatNavigationItems"
          :key="item.path"
          class="dashboard-shortcuts__item"
          type="button"
          @click="router.push(item.path)"
        >
          <div class="shortcut-icon-wrapper">
            <el-icon class="dashboard-shortcuts__icon"><component :is="item.icon" /></el-icon>
          </div>
          <div class="shortcut-content">
            <strong>{{ item.title }}</strong>
            <span>{{ item.description }}</span>
          </div>
        </button>
      </div>
    </el-card>
  </PageContainer>
</template>

<style scoped lang="scss">
.dashboard-card {
  border-radius: var(--idm-radius-xl);
  box-shadow: var(--idm-shadow-card);
  border: 1px solid var(--idm-border-color-lighter);

  :deep(.el-card__header) {
    border-bottom: 1px solid var(--idm-border-color-lighter);
    padding: var(--idm-padding-lg);
    background: var(--idm-page-background-solid);
  }
}

.dashboard-section__header {
  display: flex;
  flex-direction: column;
  gap: 6px;

  strong {
    font-size: 16px;
    font-weight: 600;
    color: var(--idm-text-primary);
  }

  .idm-muted {
    font-size: 13px;
  }
}

.dashboard-shortcuts {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--idm-padding-md);
}

.dashboard-shortcuts__item {
  display: flex;
  align-items: center;
  gap: var(--idm-padding-md);
  padding: var(--idm-padding-lg);
  border: 1px solid var(--idm-border-color-lighter);
  border-radius: var(--idm-radius-lg);
  background: #ffffff;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  text-align: left;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
    border-color: var(--idm-primary);

    .shortcut-icon-wrapper {
      background: linear-gradient(135deg, var(--idm-primary) 0%, var(--idm-primary-light) 100%);
      transform: scale(1.1);
    }

    .dashboard-shortcuts__icon {
      color: #ffffff;
    }
  }

  &:active {
    transform: translateY(-2px);
  }
}

.shortcut-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: var(--idm-radius-base);
  background: var(--idm-primary-lighter);
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.dashboard-shortcuts__icon {
  font-size: 24px;
  color: var(--idm-primary);
  transition: all 0.3s ease;
}

.shortcut-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;

  strong {
    font-size: 15px;
    font-weight: 600;
    color: var(--idm-text-primary);
    line-height: 1.4;
  }

  span {
    font-size: 13px;
    color: var(--idm-text-secondary);
    line-height: 1.5;
  }
}

@media (max-width: 1440px) {
  .dashboard-shortcuts {
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  }
}

@media (max-width: 768px) {
  .dashboard-shortcuts {
    grid-template-columns: 1fr;
  }
}
</style>
