<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { CreateDepartmentPayload, DepartmentDetail, DepartmentTreeOption, UpdateDepartmentPayload } from '@/types/department'

interface DepartmentFormValue {
  deptCode: string
  deptName: string
  parentDeptCode: string
  externalId: string
  status: number
}

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  loading?: boolean
  department?: DepartmentDetail | null
  departmentOptions: DepartmentTreeOption[]
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  submit: [CreateDepartmentPayload | UpdateDepartmentPayload]
}>()

const formRef = ref<FormInstance>()
const form = reactive<DepartmentFormValue>(buildDefaultForm())

const isCreate = computed(() => props.mode === 'create')
const title = computed(() => (isCreate.value ? '新增部门' : '编辑部门'))

const rules = computed<FormRules<DepartmentFormValue>>(() => ({
  deptCode: isCreate.value ? [{ required: true, message: '请输入部门编码', trigger: 'blur' }] : [],
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
}))

watch(
  () => [props.modelValue, props.mode, props.department] as const,
  ([visible]) => {
    if (!visible) {
      return
    }

    Object.assign(form, buildDefaultForm())

    if (props.department) {
      form.deptCode = props.department.deptCode
      form.deptName = props.department.deptName
      form.parentDeptCode = props.department.parentDeptCode || ''
      form.externalId = props.department.externalId || ''
      form.status = props.department.status
    }

    nextTick(() => formRef.value?.clearValidate())
  },
  { immediate: true },
)

function buildDefaultForm(): DepartmentFormValue {
  return {
    deptCode: '',
    deptName: '',
    parentDeptCode: '',
    externalId: '',
    status: 1,
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  if (isCreate.value) {
    emit('submit', {
      deptCode: form.deptCode.trim(),
      deptName: form.deptName.trim(),
      parentDeptCode: form.parentDeptCode || null,
      externalId: form.externalId.trim() || null,
    })
    return
  }

  emit('submit', {
    deptName: form.deptName.trim(),
    parentDeptCode: form.parentDeptCode || null,
    status: form.status,
  })
}

function closeDrawer() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-drawer :model-value="modelValue" :title="title" size="500px" @close="closeDrawer">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item v-if="isCreate" label="部门编码" prop="deptCode">
        <el-input v-model="form.deptCode" placeholder="请输入部门编码" />
      </el-form-item>

      <el-form-item v-else label="部门编码">
        <el-input :model-value="form.deptCode" disabled />
      </el-form-item>

      <el-form-item label="部门名称" prop="deptName">
        <el-input v-model="form.deptName" placeholder="请输入部门名称" />
      </el-form-item>

      <el-form-item label="上级部门">
        <el-tree-select
          v-model="form.parentDeptCode"
          :data="departmentOptions"
          check-strictly
          clearable
          default-expand-all
          node-key="value"
          placeholder="请选择上级部门"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item v-if="isCreate" label="外部部门 ID">
        <el-input v-model="form.externalId" placeholder="请输入外部部门 ID（可选）" />
      </el-form-item>

      <el-form-item v-else label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="closeDrawer">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">
          {{ isCreate ? '创建部门' : '保存修改' }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
