<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { RoleOption } from '@/types/role'
import type { UserItem } from '@/types/user'

const props = defineProps<{
  modelValue: boolean
  loading?: boolean
  user?: UserItem | null
  roleOptions: RoleOption[]
  roleIds: number[]
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  submit: [number[]]
}>()

const selectedRoleIds = ref<number[]>([])

const activeRoleOptions = computed(() => props.roleOptions.filter((role) => role.status === 1))
const activeRoleIdSet = computed(() => new Set(activeRoleOptions.value.map((role) => role.id)))

watch(
  () => [props.modelValue, props.roleIds] as const,
  ([visible, roleIds]) => {
    if (!visible) {
      return
    }
    selectedRoleIds.value = roleIds.filter((roleId) => activeRoleIdSet.value.has(roleId))
  },
  { immediate: true },
)

function closeDrawer() {
  emit('update:modelValue', false)
}

function handleSubmit() {
  emit('submit', [...selectedRoleIds.value])
}
</script>

<template>
  <el-drawer :model-value="modelValue" title="分配角色" size="440px" @close="closeDrawer">
    <template v-if="user">
      <el-alert :closable="false" show-icon type="info">
        当前用户：{{ user.realName }}（{{ user.username }}）
      </el-alert>

      <el-form class="role-drawer__form" label-position="top">
        <el-form-item label="角色列表">
          <el-select
            v-model="selectedRoleIds"
            clearable
            collapse-tags
            collapse-tags-tooltip
            filterable
            multiple
            placeholder="请选择角色"
            style="width: 100%"
          >
            <el-option
              v-for="role in activeRoleOptions"
              :key="role.id"
              :label="`${role.roleName} (${role.roleCode})`"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </template>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="closeDrawer">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">保存角色</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.role-drawer__form {
  margin-top: 16px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
