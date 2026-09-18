import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import pinia from './stores'
import './styles/index.scss'
import {
  createPersistentTableScrollCoordinator,
  PersistentTableScrollCoordinatorKey,
} from './components/table-scroll/persistent-table-scroll-coordinator'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 4xx（401/403/404 等确定性失败）不重试，其余（5xx/网络抖动）最多重试 1 次：
      // 避免已下线接口的 404 被默认 3 次重试放大成整屏重复报错。
      retry: (failureCount, error) => {
        const status = (error as { response?: { status?: number } } | null)?.response?.status
        if (typeof status === 'number' && status >= 400 && status < 500) {
          return false
        }
        return failureCount < 1
      },
    },
  },
})

const app = createApp(App)

app.use(pinia)
app.use(router)
app.use(ElementPlus)
app.use(VueQueryPlugin, { queryClient })
app.provide(PersistentTableScrollCoordinatorKey, createPersistentTableScrollCoordinator())
app.mount('#app')
