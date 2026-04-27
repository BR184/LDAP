<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { executeFeishuFullImport } from '@/api/modules/system-import'
import type { ImportMode } from '@/types/system-import'

const router = useRouter()
const queryClient = useQueryClient()
const formRef = ref<FormInstance>()

const form = reactive({
  documentPath: '',
  remark: 'manual-full-import',
})

const rules: FormRules<typeof form> = {
  documentPath: [{ required: true, message: '请输入受控目录中的导入文件路径', trigger: 'blur' }],
}

const importMutation = useMutation({
  mutationFn: executeFeishuFullImport,
  onSuccess: async (detail, payload) => {
    await queryClient.invalidateQueries({ queryKey: ['sync', 'jobs'] })
    ElMessage.success(
      payload.importMode === 'ALIGN'
        ? `对齐导入已触发，批次号：${detail.batch.batchNo}`
        : `补充导入已触发，批次号：${detail.batch.batchNo}`,
    )
  },
})

const actionLoading = computed(() => importMutation.isPending.value)

async function handleSubmit(importMode: ImportMode) {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  if (importMode === 'ALIGN') {
    try {
      await ElMessageBox.confirm(
        '对齐导入会以文件为准清理历史 FEISHU 用户和 FEISHU 部门，请确认当前花名册或 JSON 包已经完整准备。',
        '确认对齐导入',
        {
          type: 'warning',
          confirmButtonText: '确认导入',
          cancelButtonText: '取消',
        },
      )
    } catch {
      return
    }
  }

  const detail = await importMutation.mutateAsync({
    documentPath: form.documentPath.trim(),
    remark: form.remark.trim() || undefined,
    importMode,
  })

  await ElMessageBox.alert(
    [
      `批次号：${detail.batch.batchNo}`,
      `批次类型：${detail.batch.batchType}`,
      `批次状态：${detail.batch.status}`,
      `任务数量：${detail.jobs.length}`,
      `差异数量：${detail.diffs.length}`,
    ].join('\n'),
    importMode === 'ALIGN' ? '对齐导入已提交' : '补充导入已提交',
    {
      type: detail.batch.status === 'FAIL' ? 'error' : 'success',
      confirmButtonText: '查看同步任务',
    },
  )

  router.push('/sync/jobs')
}
</script>

<template>
  <PageContainer title="文件导入" description="统一受理飞书组织数据的一键导入，支持补充导入与对齐导入两种标准模式。">
    <div class="system-import-layout">
      <el-card class="idm-card" shadow="never">
        <template #header>
          <div class="section-header">
            <strong>导入说明</strong>
            <span class="idm-muted">支持飞书花名册 XLSX 文件，以及统一 JSON 包两种输入格式。</span>
          </div>
        </template>

        <div class="mode-grid">
          <el-alert :closable="false" show-icon type="info" title="补充导入">
            文件中的部门和用户会新增或更新；历史 FEISHU 数据即使不在本次文件中，也会继续保留。
          </el-alert>
          <el-alert :closable="false" show-icon type="warning" title="对齐导入">
            文件中的部门和用户会新增、更新、迁移，并清理文件里已经不存在的历史 FEISHU 数据。
          </el-alert>
        </div>
      </el-card>

      <el-card class="idm-card" shadow="never">
        <template #header>
          <div class="section-header">
            <strong>导入参数</strong>
            <span class="idm-muted">文件路径必须位于后端配置的受控目录内，例如 `docs/feishu-import`。</span>
          </div>
        </template>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="导入文件路径" prop="documentPath">
            <el-input
              v-model="form.documentPath"
              placeholder="例如 花名册 2026-04-22_112145.xlsx 或 bundle/full-demo.json"
            />
          </el-form-item>

          <el-form-item label="批次备注">
            <el-input v-model="form.remark" placeholder="可选，默认 manual-full-import" />
          </el-form-item>

          <div class="form-actions">
            <el-button
              type="primary"
              :icon="UploadFilled"
              :loading="actionLoading"
              @click="handleSubmit('SUPPLEMENT')"
            >
              补充导入
            </el-button>
            <el-button
              type="warning"
              :icon="UploadFilled"
              :loading="actionLoading"
              @click="handleSubmit('ALIGN')"
            >
              对齐导入
            </el-button>
          </div>
        </el-form>
      </el-card>
    </div>
  </PageContainer>
</template>

<style scoped lang="scss">
.system-import-layout {
  display: grid;
  gap: 16px;
}

.section-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 960px) {
  .mode-grid {
    grid-template-columns: 1fr;
  }

  .form-actions {
    flex-direction: column;
  }
}
</style>
