import axios, { type AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import pinia from '@/stores'
import { useAuthStore } from '@/stores/auth'
import type { ApiEnvelope } from '@/types/api'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const authStore = useAuthStore(pinia)

  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }

  return config
})

request.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiEnvelope<unknown>

    if (payload && typeof payload.success === 'boolean') {
      if (payload.success) {
        return payload.data
      }

      ElMessage.error(payload.message || '请求失败')
      return Promise.reject(new Error(payload.message || payload.code || 'REQUEST_FAILED'))
    }

    return response.data
  },
  (error: AxiosError<ApiEnvelope<never>>) => {
    const authStore = useAuthStore(pinia)
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '请求失败'

    if (status === 401) {
      ElMessage.error(message)
      authStore.clearSession()

      if (window.location.pathname !== '/login') {
        const redirect = window.location.pathname + window.location.search
        window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`
      }
    } else if (status === 403) {
      ElMessage.error('当前账号没有权限执行此操作')
    } else {
      ElMessage.error(message)
    }

    return Promise.reject(error)
  },
)

export default request
