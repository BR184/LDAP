import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AdminLayout from '@/layout/AdminLayout.vue'
import pinia from '@/stores'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: {
      title: '登录',
      requiresAuth: false,
    },
  },
  {
    path: '/',
    component: AdminLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/user/UserListView.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'departments',
        name: 'departments',
        component: () => import('@/views/department/DepartmentTreeView.vue'),
        meta: { title: '部门管理' },
      },
      {
        path: 'roles',
        name: 'roles',
        component: () => import('@/views/role/RoleListView.vue'),
        meta: { title: '角色管理' },
      },
      {
        path: 'menus',
        name: 'menus',
        component: () => import('@/views/menu/MenuTreeView.vue'),
        meta: { title: '菜单管理' },
      },
      {
        path: 'permissions',
        name: 'permissions',
        component: () => import('@/views/permission/PermissionTreeView.vue'),
        meta: { title: '权限树' },
      },
      {
        path: 'sync/jobs',
        name: 'sync-jobs',
        component: () => import('@/views/sync/SyncJobListView.vue'),
        meta: { title: '同步任务' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/profile/ProfileView.vue'),
        meta: { title: '个人中心', hideInMenu: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/system/NotFoundView.vue'),
    meta: {
      title: '页面不存在',
      requiresAuth: false,
    },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore(pinia)
  const requiresAuth = to.meta.requiresAuth !== false

  if (!requiresAuth) {
    if (to.path === '/login' && authStore.token) {
      return '/dashboard'
    }

    return true
  }

  if (!authStore.token) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  if (!authStore.profileLoaded) {
    try {
      await authStore.loadProfile()
    } catch {
      return {
        path: '/login',
        query: {
          redirect: to.fullPath,
        },
      }
    }
  }

  return true
})

export default router
