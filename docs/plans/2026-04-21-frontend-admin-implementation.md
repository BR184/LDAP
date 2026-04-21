# Frontend Admin Skeleton Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 创建基于 Vue 3 + Element Plus 的后台前端工程骨架，并将前端方案回写到总设计文档。

**Architecture:** 前端采用 `frontend/` 子目录与后端单仓共存，先完成工程初始化、布局壳子、路由与核心占位页，再将设计方法写入总设计文档，保证实现与设计一致。首期只搭管理台骨架，不引入高复杂度前端架构。

**Tech Stack:** Vue 3, TypeScript, Vite, Vue Router, Pinia, Element Plus, Axios, Sass

---

### Task 1: 初始化前端工程

**Files:**
- Create: `frontend/*`
- Modify: `.gitignore`

**Step 1: 创建 Vite Vue TS 工程**

Run: `npm.cmd create vite@latest frontend -- --template vue-ts`

**Step 2: 安装基础依赖**

Run: `npm.cmd install`

**Step 3: 安装核心业务依赖**

Run: `npm.cmd install vue-router pinia element-plus axios @vueuse/core @tanstack/vue-query pinia-plugin-persistedstate @element-plus/icons-vue`

**Step 4: 安装开发依赖**

Run: `npm.cmd install -D sass unplugin-auto-import unplugin-vue-components`

### Task 2: 搭建后台壳子

**Files:**
- Modify: `frontend/src/*`
- Create: `frontend/src/layout/*`
- Create: `frontend/src/router/*`
- Create: `frontend/src/stores/*`
- Create: `frontend/src/api/*`

**Step 1: 配置全局入口和主题样式**

**Step 2: 配置路由、登录守卫和基础布局**

**Step 3: 配置认证状态和请求拦截器**

### Task 3: 创建首期占位页面

**Files:**
- Create: `frontend/src/views/auth/*`
- Create: `frontend/src/views/dashboard/*`
- Create: `frontend/src/views/user/*`
- Create: `frontend/src/views/department/*`
- Create: `frontend/src/views/role/*`
- Create: `frontend/src/views/menu/*`
- Create: `frontend/src/views/permission/*`
- Create: `frontend/src/views/sync/*`

**Step 1: 完成登录页**

**Step 2: 完成首页与主导航**

**Step 3: 完成核心业务页占位**

### Task 4: 回写总设计文档

**Files:**
- Modify: `docs/project-design.md`
- Reference: `docs/plans/2026-04-21-frontend-admin-design.md`

**Step 1: 在总设计文档中补充前端章节**

**Step 2: 写入技术栈、信息架构、页面模板与实施顺序**

### Task 5: 校验

**Files:**
- Verify: `frontend/package.json`
- Verify: `frontend/src/*`
- Verify: `docs/project-design.md`

**Step 1: 运行前端构建**

Run: `npm.cmd run build`

**Step 2: 检查关键文件是否生成**

**Step 3: 汇总结果**
