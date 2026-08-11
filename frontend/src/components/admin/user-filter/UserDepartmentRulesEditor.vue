<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import type { DepartmentTreeNode } from '@/types/department'
import type { DepartmentRuleDraft, MembershipScope, RuleMode, TreeScope } from './types'
import { nextRuleId } from './types'

const props = defineProps<{
  modelValue: DepartmentRuleDraft[]
  departments: DepartmentTreeNode[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: DepartmentRuleDraft[]): void
}>()

const MAX_RULES = 20
const treeRef = ref<{ filter: (query: string) => void } | null>(null)
const keyword = ref('')
const treeProps = { label: 'deptName', children: 'children' }

const rules = computed(() => props.modelValue)

/** 部门代码 → 名称路径（用于规则消歧展示） */
const pathMap = computed(() => {
  const map = new Map<string, string[]>()
  const walk = (nodes: DepartmentTreeNode[], parentPath: string[]) => {
    for (const node of nodes) {
      const path = [...parentPath, node.deptName]
      map.set(node.deptCode, path)
      if (node.children?.length) {
        walk(node.children, path)
      }
    }
  }
  walk(props.departments, [])
  return map
})

/** 同级按部门名称排序（locale zh 优先），仅作用于编辑器展示 */
const sortedDepartments = computed(() => sortSiblings(props.departments))

function sortSiblings(nodes: DepartmentTreeNode[]): DepartmentTreeNode[] {
  const sorted = [...nodes].sort((left, right) => left.deptName.localeCompare(right.deptName, 'zh'))
  return sorted.map((node) =>
    node.children?.length ? { ...node, children: sortSiblings(node.children) } : node,
  )
}

function update(value: DepartmentRuleDraft[]) {
  emit('update:modelValue', value)
}

function addRule(rule: Omit<DepartmentRuleDraft, 'id'>) {
  if (rules.value.length >= MAX_RULES) {
    ElMessage.warning(`部门规则最多 ${MAX_RULES} 条`)
    return
  }
  update([...rules.value, { ...rule, id: nextRuleId() }])
}

function handleNodeClick(data: DepartmentTreeNode) {
  if (data.status !== 1) {
    return
  }
  addRule({
    mode: 'include',
    deptCode: data.deptCode,
    deptName: data.deptName,
    deptPath: (pathMap.value.get(data.deptCode) ?? [data.deptName]).join(' / '),
    treeScope: 'SUBTREE',
    membershipScope: 'ANY',
  })
}

function addUnassignedRule() {
  addRule({
    mode: 'exclude',
    deptCode: '',
    deptName: '未归属部门',
    treeScope: 'EXACT',
    membershipScope: 'ANY',
    unassigned: true,
  })
}

function removeRule(id: string) {
  update(rules.value.filter((rule) => rule.id !== id))
}

function filterNode(value: string, data: { deptName?: string; deptCode?: string }) {
  if (!value) return true
  return (data.deptName ?? '').includes(value) || (data.deptCode ?? '').includes(value)
}

function onKeywordChange() {
  treeRef.value?.filter(keyword.value)
}

const membershipOptions: { value: MembershipScope; label: string }[] = [
  { value: 'ANY', label: '主或兼职' },
  { value: 'PRIMARY', label: '仅主部门' },
  { value: 'PART_TIME', label: '仅兼职部门' },
]

const treeScopeOptions: { value: TreeScope; label: string }[] = [
  { value: 'EXACT', label: '仅本部门' },
  { value: 'SUBTREE', label: '含下级部门' },
]

const modeOptions: { value: RuleMode; label: string }[] = [
  { value: 'include', label: '包含' },
  { value: 'exclude', label: '排除' },
]
</script>

<template>
  <div class="dept-rules-editor">
    <div class="dept-rules-editor__tree">
      <div class="dept-rules-editor__toolbar">
        <el-input v-model="keyword" size="small" clearable placeholder="搜索部门" @input="onKeywordChange" />
        <el-button size="small" :icon="Plus" @click="addUnassignedRule">未归属部门</el-button>
      </div>
      <el-tree
        ref="treeRef"
        :data="sortedDepartments"
        :props="treeProps"
        node-key="deptCode"
        :filter-node-method="filterNode"
        default-expand-all
        class="dept-rules-editor__tree-body"
        @node-click="handleNodeClick"
      >
        <template #default="{ data }">
          <span class="dept-node" :class="{ 'is-disabled': data.status !== 1 }">
            {{ data.deptName }}
            <span class="dept-node__code">{{ data.deptCode }}</span>
          </span>
        </template>
      </el-tree>
    </div>

    <div class="dept-rules-editor__rules">
      <div class="dept-rules-editor__rules-title">
        已选规则（{{ rules.length }}/{{ MAX_RULES }}）
        <span class="idm-muted">点击左侧部门添加，支持包含/排除</span>
      </div>
      <div v-if="!rules.length" class="dept-rules-editor__empty">尚未添加部门规则，即不限制部门</div>
      <div v-for="rule in rules" :key="rule.id" class="dept-rule">
        <div class="dept-rule__name" :title="rule.deptPath || rule.deptName">
          {{ rule.unassigned ? '未归属部门' : rule.deptPath || rule.deptName }}
        </div>
        <div class="dept-rule__ops">
          <el-select v-model="rule.mode" size="small" style="width: 84px">
            <el-option v-for="option in modeOptions" :key="option.value" :value="option.value" :label="option.label" />
          </el-select>
          <el-select v-if="!rule.unassigned" v-model="rule.treeScope" size="small" style="width: 108px">
            <el-option v-for="option in treeScopeOptions" :key="option.value" :value="option.value" :label="option.label" />
          </el-select>
          <el-select v-if="!rule.unassigned" v-model="rule.membershipScope" size="small" style="width: 118px">
            <el-option v-for="option in membershipOptions" :key="option.value" :value="option.value" :label="option.label" />
          </el-select>
          <el-button size="small" :icon="Delete" circle plain type="danger" @click="removeRule(rule.id)" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dept-rules-editor {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px;

  &__tree,
  &__rules {
    min-height: 220px;
  }

  &__tree {
    border-right: 1px dashed var(--el-border-color-lighter);
    padding-right: 12px;
  }

  &__toolbar {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
  }

  &__tree-body {
    max-height: 260px;
    overflow: auto;
  }

  &__rules-title {
    font-weight: 600;
    margin-bottom: 8px;
    display: flex;
    gap: 8px;
    align-items: baseline;
  }

  &__empty {
    color: var(--el-text-color-secondary);
    font-size: 13px;
    padding: 12px 0;
  }
}

.dept-node {
  &__code {
    color: var(--el-text-color-secondary);
    font-size: 12px;
    margin-left: 4px;
  }

  &.is-disabled {
    color: var(--el-text-color-disabled);
  }
}

.dept-rule {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
  overflow: hidden;

  &__name {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
  }

  &__ops {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
  }
}
</style>
