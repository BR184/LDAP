import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import { VueQueryPlugin } from '@tanstack/vue-query'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import pinia from './stores'
import './styles/index.scss'
import {
  createPersistentTableScrollCoordinator,
  PersistentTableScrollCoordinatorKey,
} from './components/table-scroll/persistent-table-scroll-coordinator'

const app = createApp(App)

app.use(pinia)
app.use(router)
app.use(ElementPlus)
app.use(VueQueryPlugin)
app.provide(PersistentTableScrollCoordinatorKey, createPersistentTableScrollCoordinator())
app.mount('#app')
