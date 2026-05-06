<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { DepartmentTreeOption } from '@/types/department'
import type { RoleOption } from '@/types/role'
import type { CreateUserPayload, UpdateUserPayload, UserItem } from '@/types/user'

interface UserFormValue {
  username: string
  realName: string
  email: string
  mobile: string
  employeeNo: string
  deptCode: string
  partTimeDeptCodes: string[]
  initialPassword: string
  roleIds: number[]
}

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  loading?: boolean
  user?: UserItem | null
  roleOptions: RoleOption[]
  departmentOptions: DepartmentTreeOption[]
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  submit: [CreateUserPayload | UpdateUserPayload]
}>()

const formRef = ref<FormInstance>()
const form = reactive<UserFormValue>(buildDefaultForm())

const isCreate = computed(() => props.mode === 'create')
const title = computed(() => (isCreate.value ? '新增用户' : '编辑用户'))

const rules = computed<FormRules<UserFormValue>>(() => ({
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [
    { required: true, message: '工作邮箱不能为空', trigger: 'blur' },
    { type: 'email', message: '请输入合法工作邮箱', trigger: 'blur' },
  ],
  mobile: [
    {
      pattern: /^$|^1\d{10}$/,
      message: '请输入 11 位合法手机号',
      trigger: 'blur',
    },
  ],
  employeeNo: [{ required: true, message: '工号不能为空', trigger: 'blur' }],
  initialPassword: isCreate.value
    ? [{ required: true, message: '初始密码不能为空', trigger: 'blur' }]
    : [],
  roleIds: isCreate.value
    ? [{ type: 'array', required: true, min: 1, message: '请至少选择一个角色', trigger: 'change' }]
    : [],
}))

watch(
  () => [props.modelValue, props.mode, props.user] as const,
  ([visible]) => {
    if (!visible) {
      return
    }

    Object.assign(form, buildDefaultForm())

    if (props.user) {
      form.username = props.user.username
      form.realName = props.user.realName
      form.email = props.user.email || ''
      form.mobile = props.user.mobile || ''
      form.employeeNo = props.user.employeeNo || ''
      form.deptCode = props.user.deptCode || ''
      form.partTimeDeptCodes = [...(props.user.partTimeDeptCodes || [])]
    }

    nextTick(() => formRef.value?.clearValidate())
  },
  { immediate: true },
)

function buildDefaultForm(): UserFormValue {
  return {
    username: '',
    realName: '',
    email: '',
    mobile: '',
    employeeNo: '',
    deptCode: '',
    partTimeDeptCodes: [],
    initialPassword: '123456',
    roleIds: [],
  }
}

function normalizePartTimeDeptCodes() {
  const seen = new Set<string>()
  return form.partTimeDeptCodes
    .map((code) => code.trim())
    .filter((code) => code.length > 0)
    .filter((code) => {
      if (code === form.deptCode) {
        return false
      }
      if (seen.has(code)) {
        return false
      }
      seen.add(code)
      return true
    })
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  const partTimeDeptCodes = normalizePartTimeDeptCodes()

  if (isCreate.value) {
    emit('submit', {
      realName: form.realName.trim(),
      email: form.email.trim(),
      mobile: form.mobile.trim(),
      employeeNo: form.employeeNo.trim(),
      deptCode: form.deptCode,
      partTimeDeptCodes,
      initialPassword: '123456',
      roleIds: [...form.roleIds],
    })
    return
  }

  emit('submit', {
    realName: form.realName.trim(),
    email: form.email.trim(),
    mobile: form.mobile.trim(),
    employeeNo: form.employeeNo.trim(),
    deptCode: form.deptCode,
    partTimeDeptCodes,
  })
}

function closeDrawer() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-drawer :model-value="modelValue" :title="title" size="520px" @close="closeDrawer">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-alert v-if="isCreate" :closable="false" class="form-alert" type="info">
        登录用户名将按“姓名拼音 + 工号”自动生成，例如 `zhangsan1001`。
      </el-alert>

      <el-form-item v-if="!isCreate" label="用户名">
        <el-input :model-value="form.username" disabled />
      </el-form-item>

      <el-form-item label="姓名" prop="realName">
        <el-input v-model="form.realName" placeholder="请输入姓名" />
      </el-form-item>

      <el-form-item label="工号" prop="employeeNo">
        <el-input v-model="form.employeeNo" placeholder="请输入工号" />
      </el-form-item>

      <el-form-item label="工作邮箱" prop="email">
        <el-input v-model="form.email" placeholder="请输入工作邮箱" />
      </el-form-item>

      <el-form-item label="手机号" prop="mobile">
        <el-input v-model="form.mobile" maxlength="11" placeholder="请输入 11 位手机号" />
      </el-form-item>

      <el-form-item label="主部门">
        <el-tree-select
          v-model="form.deptCode"
          :data="departmentOptions"
          check-strictly
          clearable
          default-expand-all
          node-key="value"
          placeholder="请选择主部门"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="兼职部门">
        <el-tree-select
          v-model="form.partTimeDeptCodes"
          :data="departmentOptions"
          check-strictly
          clearable
          default-expand-all
          multiple
          node-key="value"
          placeholder="请选择兼职部门"
          style="width: 100%"
        />
      </el-form-item>

      <template v-if="isCreate">
        <el-form-item label="初始密码（固定为 123456）" prop="initialPassword">
          <el-input :model-value="form.initialPassword" disabled show-password />
        </el-form-item>

        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" clearable filterable multiple placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="`${role.roleName} (${role.roleCode})`"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </template>
    </el-form>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="closeDrawer">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">
          {{ isCreate ? '创建用户' : '保存修改' }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.form-alert {
  margin-bottom: 16px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
