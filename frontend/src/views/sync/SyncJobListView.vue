<script setup lang="ts">
import { notifyPlanned } from '@/utils/placeholder'

const rows = [
  {
    id: 1001,
    batchNo: 'LDAP_RECONCILE_202604210001',
    jobType: 'LDAP_RECONCILE_USER',
    status: 'SUCCESS',
    operator: 'admin',
  },
  {
    id: 1002,
    batchNo: 'FEISHU_IMPORT_202604210002',
    jobType: 'FEISHU_USER_IMPORT',
    status: 'RUNNING',
    operator: 'system-scheduler',
  },
]

function statusType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'RUNNING') return 'warning'
  if (status === 'FAIL') return 'danger'
  return 'info'
}
</script>

<template>
  <PageContainer title="同步任务" description="同步中心页用于查看任务执行、批次详情和对账结果，是后续飞书导入与 LDAP 对账的统一入口。">
    <template #extra>
      <el-space>
        <el-button @click="notifyPlanned('对账预览')">对账预览</el-button>
        <el-button type="primary" @click="notifyPlanned('执行对账')">执行对账</el-button>
      </el-space>
    </template>

    <el-card class="idm-card" shadow="never">
      <el-table :data="rows">
        <el-table-column prop="batchNo" label="批次号" min-width="260" />
        <el-table-column prop="jobType" label="任务类型" min-width="220" />
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" min-width="160" />
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <el-space wrap>
              <el-button link type="primary" @click="notifyPlanned(`查看批次 ${row.batchNo}`)">批次详情</el-button>
              <el-button link type="warning" @click="notifyPlanned(`重试任务 ${row.id}`)">任务重试</el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </PageContainer>
</template>
