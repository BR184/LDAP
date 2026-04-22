<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
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
  username: isCreate.value
    ? [{ required: true, message: '请输入用户名', trigger: 'blur' }]
    : [],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入合法邮箱', trigger: 'blur' }],
  mobile: [
    {
      pattern: /^$|^1\d{10}$/,
      message: '请输入 11 位合法手机号',
      trigger: 'blur',
    },
  ],
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
    initialPassword: '123456',
    roleIds: [],
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  if (isCreate.value) {
    emit('submit', {
      username: form.username.trim(),
      realName: form.realName.trim(),
      email: form.email.trim(),
      mobile: form.mobile.trim(),
      employeeNo: form.employeeNo.trim(),
      deptCode: form.deptCode,
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
  })
}

function closeDrawer() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-drawer :model-value="modelValue" :title="title" size="520px" @close="closeDrawer">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item v-if="isCreate" label="用户名" prop="username">
        <el-input v-model="form.username" placeholder="请输入用户名" />
      </el-form-item>

      <el-form-item v-else label="用户名">
        <el-input :model-value="form.username" disabled />
      </el-form-item>

      <el-form-item label="姓名" prop="realName">
        <el-input v-model="form.realName" placeholder="请输入姓名" />
      </el-form-item>

      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="请输入邮箱" />
      </el-form-item>

      <el-form-item label="手机号" prop="mobile">
        <el-input v-model="form.mobile" maxlength="11" placeholder="请输入 11 位手机号" />
      </el-form-item>

      <el-form-item label="工号">
        <el-input v-model="form.employeeNo" placeholder="请输入工号" />
      </el-form-item>

      <el-form-item label="部门">
        <el-tree-select
          v-model="form.deptCode"
          :data="departmentOptions"
          check-strictly
          clearable
          default-expand-all
          node-key="value"
          placeholder="请选择部门"
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
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
