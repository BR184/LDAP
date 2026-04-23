<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { executeLdapPrecheck, fetchLdapFramework } from '@/api/modules/ldap'
import type { LdapPrecheckPayload, LdapPrecheckReport } from '@/types/ldap'

const formRef = ref<FormInstance>()
const precheckReport = ref<LdapPrecheckReport | null>(null)

const precheckForm = reactive<{
  systemCode: string
  enabledUsername: string
  disabledUsername: string
}>({
  systemCode: '',
  enabledUsername: '',
  disabledUsername: '',
})

const precheckRules: FormRules<typeof precheckForm> = {
  enabledUsername: [{ required: true, message: '请输入启用用户样本', trigger: 'blur' }],
}

const frameworkQuery = useQuery({
  queryKey: ['ldap', 'framework'],
  queryFn: fetchLdapFramework,
})

const precheckMutation = useMutation({
  mutationFn: executeLdapPrecheck,
  onSuccess: (result) => {
    precheckReport.value = result
    ElMessage.success(result.overallStatus === 'PASS' ? 'LDAP 预检通过' : 'LDAP 预检已完成，请查看结果')
  },
})

const framework = computed(() => frameworkQuery.data.value || null)
const supportedSystems = computed(() => framework.value?.supportedSystems || [])

function statusTagType(status: string) {
  if (status === 'PASS') return 'success'
  if (status === 'FAIL') return 'danger'
  return 'info'
}

async function handlePrecheck() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  const payload: LdapPrecheckPayload = {
    enabledUsername: precheckForm.enabledUsername.trim(),
    systemCode: precheckForm.systemCode || null,
    disabledUsername: precheckForm.disabledUsername.trim() || null,
  }

  await precheckMutation.mutateAsync(payload)
}

function resetPrecheckForm() {
  precheckForm.systemCode = ''
  precheckForm.enabledUsername = ''
  precheckForm.disabledUsername = ''
  precheckReport.value = null
  formRef.value?.clearValidate()
}
</script>

<template>
  <PageContainer title="LDAP 控制面" description="查看统一 LDAP 接入框架，并在联调前执行基础预检。">
    <div class="ldap-grid">
      <el-card class="idm-card" shadow="never">
        <template #header>
          <div class="section-header">
            <strong>框架说明</strong>
            <span class="idm-muted">统一 LDAP 接入约定，仅保留框架说明与联调预检能力</span>
          </div>
        </template>

        <el-skeleton v-if="frameworkQuery.isLoading.value" animated :rows="8" />

        <template v-else-if="framework">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="运行模式">{{ framework.mode }}</el-descriptions-item>
            <el-descriptions-item label="授权模式">{{ framework.authorizationMode }}</el-descriptions-item>
            <el-descriptions-item label="Base DN" :span="2">{{ framework.baseDn }}</el-descriptions-item>
            <el-descriptions-item label="用户目录" :span="2">{{ framework.userBase }}</el-descriptions-item>
            <el-descriptions-item label="分组目录" :span="2">{{ framework.groupBase }}</el-descriptions-item>
            <el-descriptions-item label="登录字段">{{ framework.loginAttr }}</el-descriptions-item>
            <el-descriptions-item label="统一过滤器">{{ framework.userFilter }}</el-descriptions-item>
            <el-descriptions-item label="支持系统" :span="2">
              <el-space wrap>
                <el-tag v-for="system in supportedSystems" :key="system" type="info">{{ system }}</el-tag>
              </el-space>
            </el-descriptions-item>
          </el-descriptions>
        </template>

        <el-empty v-else description="LDAP 框架信息暂不可用" />
      </el-card>

      <el-card class="idm-card" shadow="never">
        <template #header>
          <div class="section-header">
            <strong>联调预检</strong>
            <span class="idm-muted">先验证目录连通性、过滤器与启用/禁用用户样本，再进入系统联调</span>
          </div>
        </template>

        <el-form ref="formRef" :model="precheckForm" :rules="precheckRules" label-position="top">
          <el-form-item label="系统类型">
            <el-select v-model="precheckForm.systemCode" clearable placeholder="可选，默认按通用 LDAP 预检" style="width: 100%">
              <el-option v-for="system in supportedSystems" :key="system" :label="system" :value="system" />
            </el-select>
          </el-form-item>

          <el-form-item label="启用用户样本" prop="enabledUsername">
            <el-input v-model="precheckForm.enabledUsername" placeholder="请输入一个可正常登录的用户名" />
          </el-form-item>

          <el-form-item label="禁用用户样本">
            <el-input v-model="precheckForm.disabledUsername" placeholder="可选，用于校验禁用用户过滤效果" />
          </el-form-item>

          <div class="precheck-actions">
            <el-button @click="resetPrecheckForm">重置</el-button>
            <el-button type="primary" :icon="Promotion" :loading="precheckMutation.isPending.value" @click="handlePrecheck">
              执行预检
            </el-button>
          </div>
        </el-form>
      </el-card>
    </div>

    <el-card class="idm-card" shadow="never">
      <template #header>
        <div class="section-header">
          <strong>预检结果</strong>
          <span class="idm-muted">展示总体状态、过滤条件与每个检查项明细</span>
        </div>
      </template>

      <template v-if="precheckReport">
        <div class="report-summary">
          <el-tag :type="statusTagType(precheckReport.overallStatus)" size="large">
            总体状态：{{ precheckReport.overallStatus }}
          </el-tag>
          <el-tag type="info" size="large">过滤器：{{ precheckReport.userFilter }}</el-tag>
          <el-tag type="info" size="large">模式：{{ precheckReport.mode }}</el-tag>
        </div>

        <el-table :data="precheckReport.items" border>
          <el-table-column prop="name" label="检查项" min-width="220" />
          <el-table-column label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="code" label="编码" min-width="220" />
          <el-table-column prop="detail" label="结果说明" min-width="420" show-overflow-tooltip />
        </el-table>
      </template>

      <el-empty v-else description="尚未执行 LDAP 预检" />
    </el-card>
  </PageContainer>
</template>

<style scoped lang="scss">
.ldap-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}

.section-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.precheck-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.report-summary {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
</style>
