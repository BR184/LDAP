<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { TreeInstance } from 'element-plus'
import type { PermissionTreeNode, RolePermissionBundle } from '@/types/permission'
import type { RoleItem } from '@/types/role'
import {
  applyPermissionSuiteLevel,
  buildPermissionSuiteCategories,
  resolvePermissionSuiteLevel,
  type PermissionSuiteCategory,
  type SelectablePermissionSuiteLevel,
} from './permissionSuiteSelection'

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
const permissionSuiteCategories = computed(() => buildPermissionSuiteCategories(props.permissionBundles))
const permissionSuiteOptions = [
  { label: '未配置', value: 'NONE' },
  { label: '常规操作', value: 'STANDARD' },
  { label: '完整管理', value: 'ADMIN' },
]

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

function handleSuiteLevelChange(categoryId: string, rawLevel: string | number | boolean | undefined) {
  if (autoGrantFullAccess.value) return
  const level = String(rawLevel) as SelectablePermissionSuiteLevel
  if (!['NONE', 'STANDARD', 'ADMIN'].includes(level)) return
  selectedPermissionIds.value = applyPermissionSuiteLevel(
    permissionSuiteCategories.value,
    categoryId,
    level,
    selectedPermissionIds.value,
  )
  syncTreeSelection()
}

function suiteLevel(category: PermissionSuiteCategory) {
  return resolvePermissionSuiteLevel(category, effectiveCheckedPermissionIds.value)
}

function segmentedLevel(category: PermissionSuiteCategory) {
  const level = suiteLevel(category)
  return level === 'CUSTOM' ? undefined : level
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
  <el-dialog :model-value="modelValue" title="授权权限" width="920px" @close="closeDialog">
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
          <strong>模块权限套件</strong>
          <span>按职责选择授权等级</span>
        </div>
        <div class="permission-bundles__matrix">
          <div
            v-for="category in permissionSuiteCategories"
            :key="category.categoryId"
            class="permission-bundles__row"
          >
            <div class="permission-bundles__module">
              <strong>{{ category.categoryName }}</strong>
              <span>{{ category.categoryDescription }}</span>
            </div>
            <span class="permission-bundles__count">
              常规 {{ category.standard.permissionIds.length }} 项 · 完整 {{ category.admin.permissionIds.length }} 项
            </span>
            <div class="permission-bundles__control">
              <el-tag v-if="suiteLevel(category) === 'CUSTOM'" type="warning" effect="plain">自定义</el-tag>
              <el-segmented
                :model-value="segmentedLevel(category)"
                :options="permissionSuiteOptions"
                :disabled="autoGrantFullAccess"
                @change="(value) => handleSuiteLevelChange(category.categoryId, value)"
              />
            </div>
          </div>
        </div>
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
  overflow: hidden;
  border: 1px solid var(--idm-border-color);
  border-radius: var(--idm-radius-md);
  background: rgba(250, 251, 250, 0.88);
}

.permission-bundles__heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--idm-border-color-lighter);
}

.permission-bundles__heading span {
  color: var(--idm-text-secondary);
  font-size: 12px;
}

.permission-bundles__matrix {
  display: grid;
}

.permission-bundles__row {
  display: grid;
  grid-template-columns: minmax(190px, 1fr) 148px 318px;
  align-items: center;
  gap: 16px;
  min-height: 68px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--idm-border-color-lighter);
}

.permission-bundles__row:last-child {
  border-bottom: 0;
}

.permission-bundles__module {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.permission-bundles__module span,
.permission-bundles__count {
  color: var(--idm-text-secondary);
  font-size: 12px;
}

.permission-bundles__module span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-bundles__count {
  white-space: nowrap;
}

.permission-bundles__control {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.permission-bundles__control :deep(.el-segmented) {
  width: 268px;
}

.permission-bundles__control :deep(.el-segmented__item) {
  min-width: 0;
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
