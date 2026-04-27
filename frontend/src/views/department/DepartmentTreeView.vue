<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderAdd, RefreshRight } from '@element-plus/icons-vue'
import {
  createDepartment,
  deleteDepartment,
  fetchDepartmentDetail,
  fetchDepartmentTree,
  syncDepartmentToLdap,
  syncDepartmentsFromFeishu,
  updateDepartment,
} from '@/api/modules/department'
import DepartmentFormDrawer from '@/views/department/components/DepartmentFormDrawer.vue'
import type {
  CreateDepartmentPayload,
  DepartmentDetail,
  DepartmentTreeNode,
  DepartmentTreeOption,
  UpdateDepartmentPayload,
} from '@/types/department'

const queryClient = useQueryClient()

const selectedDeptCode = ref<string>('')
const detailLoading = ref(false)
const currentDepartment = ref<DepartmentDetail | null>(null)
const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')

const departmentTreeQuery = useQuery({
  queryKey: ['department-tree'],
  queryFn: fetchDepartmentTree,
})

const departmentTree = computed(() => departmentTreeQuery.data.value || [])
const departmentOptions = computed<DepartmentTreeOption[]>(() => buildDepartmentOptions(departmentTree.value))

const createDepartmentMutation = useMutation({
  mutationFn: createDepartment,
  onSuccess: async (department) => {
    ElMessage.success(`部门 ${department.deptName} 创建成功`)
    formVisible.value = false
    await refreshDepartments()
    await loadDepartmentDetail(department.deptCode)
  },
})

const updateDepartmentMutation = useMutation({
  mutationFn: ({ deptCode, payload }: { deptCode: string; payload: UpdateDepartmentPayload }) =>
    updateDepartment(deptCode, payload),
  onSuccess: async (department) => {
    ElMessage.success(`部门 ${department.deptName} 更新成功`)
    formVisible.value = false
    await refreshDepartments()
    await loadDepartmentDetail(department.deptCode)
  },
})

const syncLdapMutation = useMutation({
  mutationFn: syncDepartmentToLdap,
  onSuccess: async (department) => {
    ElMessage.success(`部门 ${department.deptName} 已同步到 LDAP`)
    await refreshDepartments()
    await loadDepartmentDetail(department.deptCode)
  },
})

const syncFeishuMutation = useMutation({
  mutationFn: syncDepartmentsFromFeishu,
})

function buildDepartmentOptions(nodes: DepartmentTreeNode[]): DepartmentTreeOption[] {
  return nodes.map((node) => ({
    value: node.deptCode,
    label: `${node.deptName} (${node.deptCode})`,
    disabled: node.status !== 1,
    children: buildDepartmentOptions(node.children || []),
  }))
}

async function refreshDepartments() {
  await queryClient.invalidateQueries({ queryKey: ['department-tree'] })
}

async function loadDepartmentDetail(deptCode: string) {
  selectedDeptCode.value = deptCode
  detailLoading.value = true
  currentDepartment.value = null

  try {
    currentDepartment.value = await fetchDepartmentDetail(deptCode)
  } finally {
    detailLoading.value = false
  }
}

function openCreate() {
  formMode.value = 'create'
  formVisible.value = true
}

function openEdit() {
  if (!currentDepartment.value) {
    return
  }
  formMode.value = 'edit'
  formVisible.value = true
}

async function handleDelete() {
  if (!currentDepartment.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认删除部门 ${currentDepartment.value.deptName}（${currentDepartment.value.deptCode}）吗？`,
      '删除部门',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )

    await deleteDepartment(currentDepartment.value.deptCode)
    ElMessage.success('部门已删除')
    selectedDeptCode.value = ''
    currentDepartment.value = null
    await refreshDepartments()
  } catch {
    // 用户取消时不额外处理
  }
}

async function handleSyncLdap() {
  if (!currentDepartment.value) {
    return
  }

  await syncLdapMutation.mutateAsync(currentDepartment.value.deptCode)
}

async function handleSyncFeishu() {
  const result = await syncFeishuMutation.mutateAsync('manual-sync')
  ElMessage.success(`部门飞书同步任务已触发，批次号：${result.batch.batchNo}`)
}

async function handleSubmit(payload: CreateDepartmentPayload | UpdateDepartmentPayload) {
  if (formMode.value === 'create') {
    await createDepartmentMutation.mutateAsync(payload as CreateDepartmentPayload)
    return
  }

  if (!currentDepartment.value) {
    return
  }

  await updateDepartmentMutation.mutateAsync({
    deptCode: currentDepartment.value.deptCode,
    payload: payload as UpdateDepartmentPayload,
  })
}
</script>

<template>
  <PageContainer title="部门管理" description="统一维护部门树、组织层级与 LDAP 分组映射，支持 CRUD、飞书同步和手工 LDAP 同步。">
    <template #extra>
      <el-space>
        <el-button :icon="RefreshRight" :loading="syncFeishuMutation.isPending.value" @click="handleSyncFeishu">
          飞书同步
        </el-button>
        <el-button type="primary" :icon="FolderAdd" @click="openCreate">新增部门</el-button>
      </el-space>
    </template>

    <div class="department-layout">
      <el-card class="idm-card department-layout__tree" shadow="never">
        <template #header>
          <div class="view-toolbar">
            <strong>部门树</strong>
            <span class="idm-muted">选择左侧节点查看详情</span>
          </div>
        </template>

        <el-tree
          v-loading="departmentTreeQuery.isLoading.value || departmentTreeQuery.isFetching.value"
          :data="departmentTree"
          node-key="deptCode"
          default-expand-all
          highlight-current
          :current-node-key="selectedDeptCode"
          :props="{ label: 'deptName', children: 'children' }"
          @node-click="(node: DepartmentTreeNode) => loadDepartmentDetail(node.deptCode)"
        />
      </el-card>

      <el-card class="idm-card" shadow="never">
        <template #header>
          <div class="view-toolbar">
            <strong>部门详情</strong>
            <div v-if="currentDepartment" class="view-toolbar__actions">
              <el-button @click="openEdit">编辑</el-button>
              <el-button type="success" :loading="syncLdapMutation.isPending.value" @click="handleSyncLdap">
                同步 LDAP
              </el-button>
              <el-button type="danger" plain @click="handleDelete">删除</el-button>
            </div>
          </div>
        </template>

        <el-skeleton :loading="detailLoading" animated :rows="8">
          <template #template>
            <el-skeleton-item variant="p" style="width: 100%; height: 24px" />
          </template>

          <template v-if="currentDepartment">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="部门名称">{{ currentDepartment.deptName }}</el-descriptions-item>
              <el-descriptions-item label="部门编码">{{ currentDepartment.deptCode }}</el-descriptions-item>
              <el-descriptions-item label="上级部门">{{ currentDepartment.parentDeptCode || '--' }}</el-descriptions-item>
              <el-descriptions-item label="层级">{{ currentDepartment.deptLevel }}</el-descriptions-item>
              <el-descriptions-item label="来源">{{ currentDepartment.sourceType }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="currentDepartment.status === 1 ? 'success' : 'danger'">
                  {{ currentDepartment.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="祖先路径" :span="2">{{ currentDepartment.ancestorPath }}</el-descriptions-item>
              <el-descriptions-item label="外部部门 ID" :span="2">{{ currentDepartment.externalId || '--' }}</el-descriptions-item>
              <el-descriptions-item label="LDAP DN" :span="2">{{ currentDepartment.ldapDn || '--' }}</el-descriptions-item>
            </el-descriptions>
          </template>

          <el-empty v-else description="请先从左侧选择部门节点" />
        </el-skeleton>
      </el-card>
    </div>

    <DepartmentFormDrawer
      v-model="formVisible"
      :mode="formMode"
      :loading="createDepartmentMutation.isPending.value || updateDepartmentMutation.isPending.value"
      :department="formMode === 'edit' ? currentDepartment : null"
      :department-options="departmentOptions"
      @submit="handleSubmit"
    />
  </PageContainer>
</template>

<style scoped lang="scss">
.department-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
}

.department-layout__tree {
  min-height: 560px;
}

.view-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.view-toolbar__actions {
  display: flex;
  gap: 12px;
}
</style>
