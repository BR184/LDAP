<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <section class="page-container">
    <header class="page-container__header">
      <div>
        <h1 class="page-container__title">{{ title }}</h1>
        <p v-if="description" class="page-container__description">{{ description }}</p>
      </div>
      <div v-if="$slots.extra" class="page-container__extra">
        <slot name="extra" />
      </div>
    </header>

    <div class="page-container__body">
      <slot />
    </div>
  </section>
</template>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: var(--idm-padding-lg);
  position: relative;
  z-index: 1;
}

.page-container__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--idm-padding-md);
  margin-bottom: var(--idm-padding-xs);
}

.page-container__title {
  margin: 0;
  color: var(--idm-text-primary);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.02em;
}

.page-container__description {
  margin: var(--idm-padding-xs) 0 0;
  color: var(--idm-text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.page-container__body {
  display: flex;
  flex-direction: column;
  gap: var(--idm-padding-lg);
}

.page-container__extra {
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .page-container__header {
    flex-direction: column;
  }

  .page-container__title {
    font-size: 24px;
  }
}
</style>
