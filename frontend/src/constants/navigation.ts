import {
  Connection,
  FolderOpened,
  Lock,
  Collection,
  Menu as MenuIcon,
  Message,
  OfficeBuilding,
  RefreshRight,
  Setting,
  User,
} from '@element-plus/icons-vue'

export interface NavigationItem {
  title: string
  path: string
  icon: object
  description: string
}

export interface NavigationGroup {
  title: string
  icon: object
  items: NavigationItem[]
}

export const flatNavigationItems: NavigationItem[] = [
  {
    title: '用户管理',
    path: '/users',
    icon: User,
    description: '管理用户、角色分配与状态维护',
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
    description: '维护角色基础信息与权限授权',
  },
  {
    title: '角色组管理',
    path: '/role-groups',
    icon: Collection,
    description: '委派维护角色成员关系与对外供给令牌',
  },
  {
    title: '菜单管理',
    path: '/menus',
    icon: MenuIcon,
    description: '维护后台导航与页面挂载关系',
  },
  {
    title: 'LDAP 控制面',
    path: '/ldap',
    icon: Connection,
    description: '查看 LDAP 接入框架并执行联调预检',
  },
  {
    title: '文件导入',
    path: '/system/imports',
    icon: FolderOpened,
    description: '统一执行飞书组织数据一键导入，支持补充导入与对齐导入',
  },
  {
    title: '邮件配置',
    path: '/system/mail-config',
    icon: Message,
    description: '维护密码重置邮件服务器、认证账号与测试发信能力',
  },
  {
    title: '同步任务',
    path: '/sync/jobs',
    icon: RefreshRight,
    description: '查看同步任务、批次与执行状态',
  },
]

export const sidebarNavigationGroups: NavigationGroup[] = [
  {
    title: '人员管理',
    icon: User,
    items: [
      {
        title: '用户管理',
        path: '/users',
        icon: User,
        description: '管理用户、角色分配与状态维护',
      },
      {
        title: '部门管理',
        path: '/departments',
        icon: OfficeBuilding,
        description: '维护部门树与 LDAP 分组映射',
      },
    ],
  },
  {
    title: '系统管理',
    icon: Setting,
    items: [
      {
        title: '菜单管理',
        path: '/menus',
        icon: MenuIcon,
        description: '维护后台导航与页面挂载关系',
      },
      {
        title: '角色管理',
        path: '/roles',
        icon: Lock,
        description: '维护角色基础信息与权限授权',
      },
      {
        title: '角色组管理',
        path: '/role-groups',
        icon: Collection,
        description: '委派维护角色成员关系与对外供给令牌',
      },
      {
        title: 'LDAP 控制面',
        path: '/ldap',
        icon: Connection,
        description: '查看 LDAP 接入框架并执行联调预检',
      },
      {
        title: '邮件配置',
        path: '/system/mail-config',
        icon: Message,
        description: '维护密码重置邮件服务器、认证账号与测试发信能力',
      },
      {
        title: '文件导入',
        path: '/system/imports',
        icon: FolderOpened,
        description: '统一执行飞书组织数据一键导入，支持补充导入与对齐导入',
      },
    ],
  },
  {
    title: '日志管理',
    icon: FolderOpened,
    items: [
      {
        title: '同步任务',
        path: '/sync/jobs',
        icon: RefreshRight,
        description: '查看同步任务、批次与执行状态',
      },
    ],
  },
]
