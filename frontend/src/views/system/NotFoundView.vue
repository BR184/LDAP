<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <el-result icon="warning" title="页面不存在" sub-title="当前访问的页面还没有配置或地址不正确。">
      <template #extra>
        <el-button type="primary" @click="router.push('/profile')">返回个人中心</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped lang="scss">
.not-found {
  display: grid;
  place-items: center;
  min-height: 70vh;
}
</style>
