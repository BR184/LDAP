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
  gap: 16px;
}

.page-container__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-container__title {
  margin: 0;
  color: var(--idm-text-primary);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.25;
}

.page-container__description {
  margin: 8px 0 0;
  color: var(--idm-text-secondary);
  font-size: 14px;
}

.page-container__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
