<script setup lang="ts">
import { useRouter } from 'vue-router'
import { flatNavigationItems } from '@/constants/navigation'

const router = useRouter()
</script>

<template>
  <PageContainer title="首页">
    <el-card class="idm-card" shadow="never">
      <template #header>
        <div class="dashboard-section__header">
          <strong>快速入口</strong>
          <span class="idm-muted">优先进入核心业务页继续接入真实数据和表单逻辑</span>
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
          <el-icon class="dashboard-shortcuts__icon"><component :is="item.icon" /></el-icon>
          <strong>{{ item.title }}</strong>
          <span>{{ item.description }}</span>
        </button>
      </div>
    </el-card>
  </PageContainer>
</template>

<style scoped lang="scss">
.dashboard-section__header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dashboard-shortcuts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}

.dashboard-shortcuts__item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-start;
  padding: 18px;
  border: 1px solid var(--idm-border-color);
  border-radius: var(--idm-radius-md);
  background: linear-gradient(180deg, #ffffff 0%, #f8faff 100%);
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.dashboard-shortcuts__item:hover {
  transform: translateY(-2px);
  box-shadow: var(--idm-shadow);
}

.dashboard-shortcuts__item span {
  color: var(--idm-text-secondary);
  text-align: left;
}

.dashboard-shortcuts__icon {
  color: var(--idm-primary);
  font-size: 18px;
}
</style>
