import { ElMessage } from 'element-plus'

export function notifyPlanned(featureName: string) {
  ElMessage.info(`${featureName} 已预留交互入口，后续接入真实接口逻辑。`)
}
