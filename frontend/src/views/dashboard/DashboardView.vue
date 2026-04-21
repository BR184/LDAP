<script setup lang="ts">
import { useRouter } from 'vue-router'
import { navigationItems } from '@/constants/navigation'

const router = useRouter()

const metrics = [
  { label: '首期页面', value: '7', helper: '已完成骨架挂载' },
  { label: '接入模块', value: '用户 / 部门 / 角色', helper: '与后端契约对齐' },
  { label: '同步中心', value: '就绪', helper: '任务与批次视图已预留' },
  { label: 'LDAP 控制面', value: '二期扩展', helper: '接口已具备' },
]
</script>

<template>
  <PageContainer
    title="首页"
    description="首期后台骨架优先服务于身份管理、权限管理和同步中心，后续逐页接入真实 API。"
  >
    <div class="idm-grid idm-grid--metrics">
      <el-card v-for="metric in metrics" :key="metric.label" class="idm-card" shadow="never">
        <div class="dashboard-metric">
          <span class="dashboard-metric__label">{{ metric.label }}</span>
          <strong class="dashboard-metric__value">{{ metric.value }}</strong>
          <span class="dashboard-metric__helper">{{ metric.helper }}</span>
        </div>
      </el-card>
    </div>

    <el-card class="idm-card" shadow="never">
      <template #header>
        <div class="dashboard-section__header">
          <strong>快速入口</strong>
          <span class="idm-muted">优先进入核心业务页继续接入真实数据和表单逻辑</span>
        </div>
      </template>

      <div class="dashboard-shortcuts">
        <button
          v-for="item in navigationItems"
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
.dashboard-metric {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dashboard-metric__label,
.dashboard-metric__helper {
  color: var(--idm-text-secondary);
}

.dashboard-metric__value {
  color: var(--idm-text-primary);
  font-size: 30px;
  line-height: 1;
}

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
