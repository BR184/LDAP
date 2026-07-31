<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { CreateMenuPayload, MenuItem, MenuTreeOption, UpdateMenuPayload } from '@/types/menu'

interface MenuFormValue {
  menuCode: string
  menuName: string
  parentId: number
  menuType: string
  path: string
  component: string
  icon: string
  sortNo: number
  remark: string
}

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  loading?: boolean
  menu?: MenuItem | null
  parentMenu?: MenuItem | null
  menuOptions: MenuTreeOption[]
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  submit: [CreateMenuPayload | UpdateMenuPayload]
}>()

const formRef = ref<FormInstance>()
const form = reactive<MenuFormValue>(buildDefaultForm())

const isCreate = computed(() => props.mode === 'create')
const title = computed(() => (isCreate.value ? '新增菜单' : '编辑菜单'))
const isCatalog = computed(() => form.menuType === 'CATALOG')

const rules = computed<FormRules<MenuFormValue>>(() => ({
  menuCode: isCreate.value ? [{ required: true, message: '请输入菜单编码', trigger: 'blur' }] : [],
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  path: [{ required: true, message: '请输入菜单路由', trigger: 'blur' }],
  component: isCatalog.value ? [] : [{ required: true, message: '请输入组件路径', trigger: 'blur' }],
}))

watch(
  () => [props.modelValue, props.mode, props.menu, props.parentMenu] as const,
  ([visible]) => {
    if (!visible) {
      return
    }

    Object.assign(form, buildDefaultForm())

    if (props.parentMenu) {
      form.parentId = props.parentMenu.id
    }

    if (props.menu) {
      form.menuCode = props.menu.menuCode
      form.menuName = props.menu.menuName
      form.parentId = props.menu.parentId
      form.menuType = props.menu.menuType
      form.path = props.menu.path
      form.component = props.menu.component || ''
      form.icon = props.menu.icon || ''
      form.sortNo = props.menu.sortNo
      form.remark = props.menu.remark || ''
    }

    if (isCatalog.value && !form.component) {
      form.component = 'Layout'
    }

    nextTick(() => formRef.value?.clearValidate())
  },
  { immediate: true },
)

watch(
  () => form.menuType,
  (menuType) => {
    if (menuType === 'CATALOG' && !form.component) {
      form.component = 'Layout'
    }
  },
)

function buildDefaultForm(): MenuFormValue {
  return {
    menuCode: '',
    menuName: '',
    parentId: 0,
    menuType: 'CATALOG',
    path: '',
    component: 'Layout',
    icon: '',
    sortNo: 0,
    remark: '',
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  const payload = {
    menuName: form.menuName.trim(),
    parentId: form.parentId,
    menuType: form.menuType,
    path: form.path.trim(),
    component: isCatalog.value ? form.component.trim() || 'Layout' : form.component.trim(),
    icon: form.icon.trim() || null,
    sortNo: form.sortNo,
    remark: form.remark.trim() || null,
  }

  if (isCreate.value) {
    emit('submit', {
      menuCode: form.menuCode.trim(),
      ...payload,
    })
    return
  }

  emit('submit', payload)
}

function closeDrawer() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-drawer :model-value="modelValue" :title="title" size="560px" @close="closeDrawer">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item v-if="isCreate" label="菜单编码" prop="menuCode">
        <el-input v-model="form.menuCode" placeholder="请输入菜单编码，例如 SYSTEM_MANAGEMENT" />
      </el-form-item>

      <el-form-item v-else label="菜单编码">
        <el-input :model-value="form.menuCode" disabled />
      </el-form-item>

      <el-form-item label="菜单名称" prop="menuName">
        <el-input v-model="form.menuName" placeholder="请输入菜单名称" />
      </el-form-item>

      <el-form-item label="上级菜单">
        <el-tree-select
          v-model="form.parentId"
          :data="menuOptions"
          check-strictly
          clearable
          default-expand-all
          node-key="value"
          placeholder="请选择上级菜单，留空表示根菜单"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="菜单类型" prop="menuType">
        <el-radio-group v-model="form.menuType">
          <el-radio value="CATALOG">目录</el-radio>
          <el-radio value="MENU">菜单</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="菜单路由" prop="path">
        <el-input v-model="form.path" placeholder="请输入菜单路由，例如 /system/menus" />
      </el-form-item>

      <el-form-item label="组件路径" prop="component">
        <el-input
          v-model="form.component"
          :placeholder="isCatalog ? '目录类型默认 Layout' : '请输入组件路径，例如 system/menu/index'"
        />
      </el-form-item>

      <el-form-item label="图标">
        <el-input v-model="form.icon" placeholder="请输入图标名称（可选）" />
      </el-form-item>

      <el-form-item label="排序值">
        <el-input-number v-model="form.sortNo" :min="0" :step="1" controls-position="right" style="width: 100%" />
      </el-form-item>

      <el-form-item label="备注">
        <el-input
          v-model="form.remark"
          :autosize="{ minRows: 3, maxRows: 5 }"
          maxlength="256"
          placeholder="请输入菜单备注（可选）"
          show-word-limit
          type="textarea"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="closeDrawer">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">
          {{ isCreate ? '创建菜单' : '保存修改' }}
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
