<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { TreeInstance } from 'element-plus'
import type { MenuTreeNode } from '@/types/menu'
import type { RoleItem } from '@/types/role'
import { normalizeMenuTreeForDisplay } from '@/utils/menu-tree'

const props = defineProps<{
  modelValue: boolean
  loading?: boolean
  initializing?: boolean
  role?: RoleItem | null
  menuTree: MenuTreeNode[]
  checkedMenuIds: number[]
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  submit: [number[]]
}>()

const treeRef = ref<TreeInstance>()

const displayMenuTree = computed(() => normalizeMenuTreeForDisplay(props.menuTree))

const autoGrantFullAccess = computed(() => (props.role?.permissionLevel || Number.MAX_SAFE_INTEGER) <= 2)
const effectiveCheckedMenuIds = computed(() =>
  autoGrantFullAccess.value ? collectMenuIds(displayMenuTree.value) : props.checkedMenuIds,
)
const checkedCount = computed(() => effectiveCheckedMenuIds.value.length)

watch(
  () => [props.modelValue, props.checkedMenuIds, displayMenuTree.value] as const,
  ([visible]) => {
    if (!visible) {
      return
    }
    nextTick(() => treeRef.value?.setCheckedKeys([...effectiveCheckedMenuIds.value]))
  },
  { immediate: true, deep: true },
)

function closeDialog() {
  emit('update:modelValue', false)
}

function handleSubmit() {
  const checkedKeys = autoGrantFullAccess.value
    ? effectiveCheckedMenuIds.value
    : ((treeRef.value?.getCheckedKeys(false) || []) as number[])
  emit('submit', checkedKeys)
}

function collectMenuIds(nodes: MenuTreeNode[]): number[] {
  const ids: number[] = []
  for (const node of nodes) {
    ids.push(node.id)
    if (node.children?.length) {
      ids.push(...collectMenuIds(node.children))
    }
  }
  return ids
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="绑定菜单" width="720px" @close="closeDialog">
    <template v-if="role">
      <el-alert :closable="false" show-icon type="info">
        当前角色：{{ role.roleName }}（{{ role.roleCode }}），当前已勾选 {{ checkedCount }} 个菜单节点。
      </el-alert>

      <el-alert
        v-if="autoGrantFullAccess"
        class="role-tree-dialog__alert"
        :closable="false"
        show-icon
        title="当前角色权限等级为 1 或 2，系统将默认授予全部菜单。"
        type="warning"
      />

      <div v-loading="initializing" class="role-tree-dialog__body">
        <el-tree
          ref="treeRef"
          :data="displayMenuTree"
          :check-strictly="true"
          default-expand-all
          node-key="id"
          show-checkbox
        >
          <template #default="{ data }">
            <div class="role-tree-dialog__node">
              <strong>{{ data.menuName }}</strong>
              <span>{{ data.menuCode }}</span>
            </div>
          </template>
        </el-tree>
      </div>
    </template>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">保存菜单绑定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.role-tree-dialog__alert {
  margin-top: 12px;
}

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
