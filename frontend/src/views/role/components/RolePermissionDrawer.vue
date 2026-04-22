<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { TreeInstance } from 'element-plus'
import type { PermissionTreeNode } from '@/types/permission'
import type { RoleItem } from '@/types/role'

const props = defineProps<{
  modelValue: boolean
  loading?: boolean
  initializing?: boolean
  role?: RoleItem | null
  permissionTree: PermissionTreeNode[]
  checkedPermissionIds: number[]
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  submit: [number[]]
}>()

const treeRef = ref<TreeInstance>()

const checkedCount = computed(() => props.checkedPermissionIds.length)

watch(
  () => [props.modelValue, props.checkedPermissionIds, props.permissionTree] as const,
  ([visible, checkedPermissionIds]) => {
    if (!visible) {
      return
    }
    nextTick(() => treeRef.value?.setCheckedKeys([...checkedPermissionIds]))
  },
  { immediate: true, deep: true },
)

function closeDialog() {
  emit('update:modelValue', false)
}

function handleSubmit() {
  const checkedKeys = (treeRef.value?.getCheckedKeys(false) || []) as number[]
  emit('submit', checkedKeys)
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="授权权限" width="760px" @close="closeDialog">
    <template v-if="role">
      <el-alert :closable="false" show-icon type="info">
        当前角色：{{ role.roleName }}（{{ role.roleCode }}），当前已勾选 {{ checkedCount }} 个权限点。
      </el-alert>

      <div v-loading="initializing" class="role-tree-dialog__body">
        <el-tree
          ref="treeRef"
          :data="permissionTree"
          :check-strictly="true"
          default-expand-all
          node-key="id"
          show-checkbox
        >
          <template #default="{ data }">
            <div class="role-tree-dialog__node role-tree-dialog__node--column">
              <strong>{{ data.permissionName }}</strong>
              <span>{{ data.permissionCode }} / {{ data.action }} {{ data.resourcePath }}</span>
            </div>
          </template>
        </el-tree>
      </div>
    </template>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">保存权限授权</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.role-tree-dialog__body {
  min-height: 360px;
  margin-top: 16px;
  padding: 16px;
  overflow: auto;
  border: 1px solid var(--idm-border-color);
  border-radius: var(--idm-radius-md);
}

.role-tree-dialog__node {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.role-tree-dialog__node--column {
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.role-tree-dialog__node span {
  color: var(--idm-text-secondary);
  font-size: 12px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
