import {
  Connection,
  DataBoard,
  Lock,
  Menu as MenuIcon,
  OfficeBuilding,
  RefreshRight,
  User,
} from '@element-plus/icons-vue'

export interface NavigationItem {
  title: string
  path: string
  icon: object
  description: string
}

export const navigationItems: NavigationItem[] = [
  {
    title: '首页',
    path: '/dashboard',
    icon: DataBoard,
    description: '查看平台概览与快速入口',
  },
  {
    title: '用户管理',
    path: '/users',
    icon: User,
    description: '管理用户、角色分配与状态',
  },
  {
    title: '部门管理',
    path: '/departments',
    icon: OfficeBuilding,
    description: '维护部门树与 LDAP 分组映射',
  },
  {
    title: '角色管理',
    path: '/roles',
    icon: Lock,
    description: '维护角色、菜单与权限绑定',
  },
  {
    title: '菜单管理',
    path: '/menus',
    icon: MenuIcon,
    description: '维护后台导航与页面挂载关系',
  },
  {
    title: '权限树',
    path: '/permissions',
    icon: Connection,
    description: '查看后端 API 权限点结构',
  },
  {
    title: '同步任务',
    path: '/sync/jobs',
    icon: RefreshRight,
    description: '查看同步任务、批次与执行状态',
  },
]
