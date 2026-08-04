<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { TreeInstance } from 'element-plus'
import type { PermissionTreeNode, RolePermissionBundle } from '@/types/permission'
import type { RoleItem } from '@/types/role'

const props = defineProps<{
  modelValue: boolean
  loading?: boolean
  initializing?: boolean
  role?: RoleItem | null
  permissionTree: PermissionTreeNode[]
  permissionBundles: RolePermissionBundle[]
  checkedPermissionIds: number[]
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  submit: [number[]]
}>()

const treeRef = ref<TreeInstance>()
const selectedPermissionIds = ref<number[]>([])

const autoGrantFullAccess = computed(() => (props.role?.permissionLevel || Number.MAX_SAFE_INTEGER) <= 2)
const effectiveCheckedPermissionIds = computed(() =>
  autoGrantFullAccess.value ? collectPermissionIds(props.permissionTree) : selectedPermissionIds.value,
)
const checkedCount = computed(() => effectiveCheckedPermissionIds.value.length)
const activeBundleIds = computed(() => {
  const selected = new Set(effectiveCheckedPermissionIds.value)
  return props.permissionBundles
    .filter((bundle) => bundle.permissionIds.every((permissionId) => selected.has(permissionId)))
    .map((bundle) => bundle.id)
})

watch(
  () => [props.modelValue, props.checkedPermissionIds, props.permissionTree] as const,
  ([visible]) => {
    if (!visible) {
      return
    }
    selectedPermissionIds.value = autoGrantFullAccess.value
      ? collectPermissionIds(props.permissionTree)
      : [...props.checkedPermissionIds]
    syncTreeSelection()
  },
  { immediate: true, deep: true },
)

function closeDialog() {
  emit('update:modelValue', false)
}

function handleSubmit() {
  emit('submit', [...effectiveCheckedPermissionIds.value])
}

function handleBundleSelectionChange(values: Array<string | number | boolean>) {
  if (autoGrantFullAccess.value) return
  const nextBundleIds = new Set(values.map(String))
  const previousBundleIds = new Set(activeBundleIds.value)
  const selectedPermissionIdSet = new Set(selectedPermissionIds.value)

  for (const bundle of props.permissionBundles) {
    if (nextBundleIds.has(bundle.id) && !previousBundleIds.has(bundle.id)) {
      bundle.permissionIds.forEach((permissionId) => selectedPermissionIdSet.add(permissionId))
    }
  }

  const retainedBundlePermissionIds = new Set(
    props.permissionBundles
      .filter((bundle) => nextBundleIds.has(bundle.id))
      .flatMap((bundle) => bundle.permissionIds),
  )
  for (const bundle of props.permissionBundles) {
    if (previousBundleIds.has(bundle.id) && !nextBundleIds.has(bundle.id)) {
      bundle.permissionIds.forEach((permissionId) => {
        if (!retainedBundlePermissionIds.has(permissionId)) selectedPermissionIdSet.delete(permissionId)
      })
    }
  }

  selectedPermissionIds.value = [...selectedPermissionIdSet]
  syncTreeSelection()
}

function handleTreeCheck(_: PermissionTreeNode, state: { checkedKeys: Array<string | number> }) {
  selectedPermissionIds.value = state.checkedKeys.map(Number)
}

function syncTreeSelection() {
  nextTick(() => treeRef.value?.setCheckedKeys([...effectiveCheckedPermissionIds.value]))
}

function collectPermissionIds(nodes: PermissionTreeNode[]): number[] {
  const ids: number[] = []
  for (const node of nodes) {
    ids.push(node.id)
    if (node.children?.length) {
      ids.push(...collectPermissionIds(node.children))
    }
  }
  return ids
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="授权权限" width="760px" @close="closeDialog">
    <template v-if="role">
      <el-alert :closable="false" show-icon type="info">
        当前角色：{{ role.roleName }}（{{ role.roleCode }}），当前已勾选 {{ checkedCount }} 个权限点。
      </el-alert>

      <el-alert
        v-if="autoGrantFullAccess"
        class="role-tree-dialog__alert"
        :closable="false"
        show-icon
        title="当前角色权限等级为 1 或 2，系统将默认授予全部权限。"
        type="warning"
      />

      <section v-if="permissionBundles.length" class="permission-bundles">
        <div class="permission-bundles__heading">
          <strong>常用权限套件</strong>
          <span>快速勾选常用能力，仍可在下方调整单项权限</span>
        </div>
        <el-checkbox-group
          :model-value="activeBundleIds"
          :disabled="autoGrantFullAccess"
          class="permission-bundles__options"
          @change="handleBundleSelectionChange"
        >
          <el-checkbox-button v-for="bundle in permissionBundles" :key="bundle.id" :value="bundle.id">
            {{ bundle.name }}
          </el-checkbox-button>
        </el-checkbox-group>
      </section>

      <div v-loading="initializing" class="role-tree-dialog__body">
        <el-tree
          ref="treeRef"
          :data="permissionTree"
          :check-strictly="true"
          default-expand-all
          node-key="id"
          show-checkbox
          @check="handleTreeCheck"
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

.permission-bundles {
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px solid var(--idm-border-color);
  border-radius: var(--idm-radius-md);
  background: rgba(248, 250, 249, 0.82);
}

.permission-bundles__heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.permission-bundles__heading span {
  color: var(--idm-text-secondary);
  font-size: 12px;
}

.permission-bundles__options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.permission-bundles__options :deep(.el-checkbox-button__inner) {
  border: 1px solid var(--idm-border-color);
  border-radius: 4px;
  box-shadow: none;
}

.role-tree-dialog__node {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.role-tree-dialog__node--column {
  width: 100%;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.role-tree-dialog__node span {
  color: var(--idm-text-secondary);
  font-size: 12px;
  line-height: 1.6;
  white-space: normal;
  word-break: break-all;
}

.role-tree-dialog__body :deep(.el-tree-node__content) {
  height: auto;
  min-height: 34px;
  padding: 6px 0;
  align-items: flex-start;
}

.role-tree-dialog__body :deep(.el-tree-node__label) {
  flex: 1;
  min-width: 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
