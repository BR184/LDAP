<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { CreateRolePayload, RoleItem, UpdateRolePayload } from '@/types/role'

interface RoleFormValue {
  roleCode: string
  roleName: string
  permissionLevel: number
  remark: string
}

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  loading?: boolean
  role?: RoleItem | null
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  submit: [CreateRolePayload | UpdateRolePayload]
}>()

const formRef = ref<FormInstance>()
const form = reactive<RoleFormValue>(buildDefaultForm())

const isCreate = computed(() => props.mode === 'create')
const title = computed(() => (isCreate.value ? '新增角色' : '编辑角色'))
const autoGrantFullAccess = computed(() => form.permissionLevel <= 2)

const rules = computed<FormRules<RoleFormValue>>(() => ({
  roleCode: isCreate.value
    ? [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
    : [],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  permissionLevel: [
    { required: true, message: '请输入权限等级', trigger: 'change' },
    {
      validator: (_rule, value, callback) => {
        if (typeof value !== 'number' || value < 1) {
          callback(new Error('权限等级最小为 1'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}))

watch(
  () => [props.modelValue, props.mode, props.role] as const,
  ([visible]) => {
    if (!visible) {
      return
    }

    Object.assign(form, buildDefaultForm())

    if (props.role) {
      form.roleCode = props.role.roleCode
      form.roleName = props.role.roleName
      form.permissionLevel = props.role.permissionLevel
      form.remark = props.role.remark || ''
    }

    nextTick(() => formRef.value?.clearValidate())
  },
  { immediate: true },
)

function buildDefaultForm(): RoleFormValue {
  return {
    roleCode: '',
    roleName: '',
    permissionLevel: 3,
    remark: '',
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  if (isCreate.value) {
    emit('submit', {
      roleCode: form.roleCode.trim(),
      roleName: form.roleName.trim(),
      permissionLevel: form.permissionLevel,
      remark: form.remark.trim() || null,
    })
    return
  }

  emit('submit', {
    roleName: form.roleName.trim(),
    permissionLevel: form.permissionLevel,
    remark: form.remark.trim() || null,
  })
}

function closeDrawer() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-drawer :model-value="modelValue" :title="title" size="500px" @close="closeDrawer">
    <template v-if="role && role.builtIn === 1">
      <el-alert
        class="role-form-drawer__alert"
        :closable="false"
        show-icon
        title="当前为内置角色，权限等级和结构属性已锁定。"
        type="warning"
      />
    </template>

    <el-alert
      v-if="autoGrantFullAccess"
      class="role-form-drawer__alert"
      :closable="false"
      show-icon
      title="权限等级为 1 或 2 时，系统会在保存后自动授予全部菜单和接口权限。"
      type="info"
    />

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item v-if="isCreate" label="角色编码" prop="roleCode">
        <el-input v-model="form.roleCode" placeholder="请输入角色编码，例如 AUDITOR" />
      </el-form-item>

      <el-form-item v-else label="角色编码">
        <el-input :model-value="form.roleCode" disabled />
      </el-form-item>

      <el-form-item label="角色名称" prop="roleName">
        <el-input v-model="form.roleName" placeholder="请输入角色名称" />
      </el-form-item>

      <el-form-item label="权限等级" prop="permissionLevel">
        <el-input-number
          v-model="form.permissionLevel"
          :disabled="role?.builtIn === 1"
          :min="1"
          :step="1"
          controls-position="right"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="备注">
        <el-input
          v-model="form.remark"
          :autosize="{ minRows: 3, maxRows: 5 }"
          maxlength="256"
          placeholder="请输入角色备注（可选）"
          show-word-limit
          type="textarea"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="closeDrawer">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">
          {{ isCreate ? '创建角色' : '保存修改' }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.role-form-drawer__alert {
  margin-bottom: 16px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
